package com.project.chirp.service

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.domain.exception.InvalidCredentialsException
import com.project.chirp.domain.exception.InvalidTokenException
import com.project.chirp.domain.exception.SamePasswordException
import com.project.chirp.domain.exception.UserNotFoundException
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.PasswordResetTokenEntity
import com.project.chirp.infra.database.repositories.PasswordResetTokenRepository
import com.project.chirp.infra.database.repositories.RefreshTokenRepository
import com.project.chirp.infra.database.repositories.UserRepository
import com.project.chirp.infra.message_queue.EventPublisher
import com.project.chirp.infra.security.PasswordEncoder
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/***
 * Service for managing password reset tokens.
 * @param userRepository: Repository to fetch user entities.
 * @param passwordResetTokenRepository: Repository for managing password reset token entities.
 * @param passwordEncoder: Password encoder for hashing passwords.
 * @param:Value("\${chirp.email.reset-password.expiry-minutes}") expiryMinutes: The number of minutes after which a password reset token expires.
 * @param expiryMinutes: The number of minutes after which a password reset token expires.
 * @param refreshTokenRepository: Repository for managing refresh token entities.
 *
 * @see requestPasswordReset: Requests a password reset for a user.
 * @see resetPassword: Resets a user's password using a password reset token.
 * @see changePassword: Changes a user's password.
 * @see cleanupExpiredTokens: Cleans up expired password reset tokens.
 *
 */
@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    @param:Value("\${chirp.email.reset-password.expiry-minutes}")
    private val expiryMinutes: Long,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val eventPublisher: EventPublisher,
) {
    @Transactional
    fun requestPasswordReset(email: String) {
        /** We don't throw an exception for security reasons, this is because we don't want to leak information about the existence of a user */
        val user = userRepository.findByEmail(email) ?: return

        /** If the user is found, we invalidate any active password reset tokens for that user */
        passwordResetTokenRepository.invalidateActiveTokensForUser(user)

        val token = PasswordResetTokenEntity(
            user = user,
            expiresAt = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES),
        )
        passwordResetTokenRepository.save(token)

        eventPublisher.publish(
            event = UserEvent.RequestResetPassword(
                userId = user.id!!,
                email = user.email,
                username = user.username,
                passwordResetToken = token.token,
                expiresInMinutes = expiryMinutes
            )
        )
    }

    @Transactional
    fun resetPassword(token: String, newPassword: String) {
        val resetToken = passwordResetTokenRepository.findByToken(token)
            ?: throw InvalidTokenException("Invalid password reset token")

        if (resetToken.isUsed) {
            throw InvalidTokenException("Email verification token is already used.")
        }

        if (resetToken.isExpired) {
            throw InvalidTokenException("Email verification token has already expired.")
        }

        val user = resetToken.user

        if (passwordEncoder.matches(newPassword, user.hashedPassword)) {
            throw SamePasswordException()
        }

        val hashedNewPassword = passwordEncoder.encode(newPassword)
        userRepository.save(
            user.apply {
                this.hashedPassword = hashedNewPassword!!
            }
        )

        passwordResetTokenRepository.save(
            resetToken.apply {
                this.usedAt = Instant.now()
            }
        )

        refreshTokenRepository.deleteByUserId(user.id!!)
    }

    @Transactional
    fun changePassword(
        userId: UserId,
        oldPassword: String,
        newPassword: String,
    ) {
        /** Here it's safe to throw an exception because we know that the user exists because logged in */
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException()

        if (!passwordEncoder.matches(oldPassword, user.hashedPassword)) {
            throw InvalidCredentialsException()
        }

        if (oldPassword == newPassword) {
            throw SamePasswordException()
        }

        refreshTokenRepository.deleteByUserId(user.id!!)

        val newHashedPassword = passwordEncoder.encode(newPassword)
        userRepository.save(
            user.apply {
                this.hashedPassword = newHashedPassword!!
            }
        )
    }

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanupExpiredTokens() {
        passwordResetTokenRepository.deleteByExpiresAtLessThan(
            now = Instant.now()
        )
    }
}