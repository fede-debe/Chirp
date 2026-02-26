package com.project.chirp.service

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.domain.exception.InvalidTokenException
import com.project.chirp.domain.exception.UserNotFoundException
import com.project.chirp.domain.model.EmailVerificationToken
import com.project.chirp.infra.database.entities.EmailVerificationTokenEntity
import com.project.chirp.infra.database.mappers.toEmailVerificationToken
import com.project.chirp.infra.database.mappers.toUser
import com.project.chirp.infra.database.repositories.EmailVerificationTokenRepository
import com.project.chirp.infra.database.repositories.UserRepository
import com.project.chirp.infra.message_queue.EventPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/***
 * Service for managing email verification tokens.
 * @param emailVerificationTokenRepository: Repository for managing email verification token entities.
 * @param userRepository: Repository for managing user entities.
 * @param expiryHours: The number of hours after which an email verification token expires.
 *
 * @see createVerificationToken: Creates a new email verification token for a user.
 * @see verifyEmail: Verifies an email verification token for a user.
 * @see cleanupExpiredTokens: Cleans up expired email verification tokens.
 * @see resendVerificationEmail: Resends an email verification token for a user.
 */
@Service
class EmailVerificationService(
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val userRepository: UserRepository,
    @param:Value("\${chirp.email.verification.expiry-hours}") private val expiryHours: Long,
    private val eventPublisher: EventPublisher
) {

    @Transactional
    fun resendVerificationEmail(email: String) {
        val token = createVerificationToken(email)

        if (token.user.hasEmailVerified) {
            return
        }

        /** Publishes a UserEvent.RequestResendVerification event for the user */
        eventPublisher.publish(
            event = UserEvent.RequestResendVerification(
                userId = token.user.id,
                email = token.user.email,
                username = token.user.username,
                verificationToken = token.token
            )
        )
    }

    @Transactional
    fun createVerificationToken(email: String): EmailVerificationToken {
        val userEntity = userRepository.findByEmail(email)
            ?: throw UserNotFoundException()

        emailVerificationTokenRepository.invalidateActiveTokensForUser(userEntity)

        val token = EmailVerificationTokenEntity(
            expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS),
            user = userEntity
        )

        return emailVerificationTokenRepository.save(token).toEmailVerificationToken()
    }

    @Transactional
    fun verifyEmail(token: String) {
        val verificationToken = emailVerificationTokenRepository.findByToken(token)
            ?: throw InvalidTokenException("Email verification token is invalid.")

        if (verificationToken.isUsed) {
            throw InvalidTokenException("Email verification token is already used.")
        }

        if (verificationToken.isExpired) {
            throw InvalidTokenException("Email verification token has already expired.")
        }

        emailVerificationTokenRepository.save(
            verificationToken.apply {
                this.usedAt = Instant.now()
            }
        )
        userRepository.save(
            verificationToken.user.apply {
                this.hasVerifiedEmail = true
            }
        ).toUser()

        /**
         * Publishes a UserEvent.Verified event for the user to observe into
         * the chat module in oder to create a new record of a char participant
         * in our DB table.
         */
        eventPublisher.publish(
            event = UserEvent.Verified(
                userId = verificationToken.user.id!!,
                email = verificationToken.user.email,
                username = verificationToken.user.username,
            )
        )
    }

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanupExpiredTokens() {
        emailVerificationTokenRepository.deleteByExpiresAtLessThan(
            now = Instant.now()
        )
    }
}