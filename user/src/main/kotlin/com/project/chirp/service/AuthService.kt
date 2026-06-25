package com.project.chirp.service

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.domain.exception.*
import com.project.chirp.domain.model.AuthenticatedUser
import com.project.chirp.domain.model.User
import com.project.chirp.infra.database.entities.UserEntity
import com.project.chirp.infra.database.mappers.toUser
import com.project.chirp.infra.database.repositories.RefreshTokenRepository
import com.project.chirp.infra.database.repositories.UserRepository
import com.project.chirp.infra.message_queue.EventPublisher
import com.project.chirp.infra.security.PasswordEncoder
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/***
 * Service for managing email/password authentication.
 *
 * Token issuance and refresh-token storage are delegated to [AuthTokenService] so that login,
 * refresh, and social sign-in all share the exact same token-issuing path.
 *
 * @see register: Registers a new user.
 * @see login: Authenticates a user and returns an AuthenticatedUser object.
 * @see refresh: Refreshes an access token using a valid refresh token.
 * @see logout: Invalidates a refresh token.
 *
 * @param userRepository: Repository for managing user data.
 * @param passwordEncoder: Encoder for hashing passwords.
 * @param jwtService: Service for generating and validating JWT tokens.
 * @param refreshTokenRepository: Repository for managing refresh tokens.
 * @param authTokenService: Shared issuer of access/refresh tokens (also used by social sign-in).
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailVerificationService: EmailVerificationService,
    private val eventPublisher: EventPublisher,
    private val authTokenService: AuthTokenService,
) {

    /***
     * Registers a new user.
     * @param email The email of the user.
     * @param username The username of the user.
     * @param password The password of the user.
     * @return The registered User object.
     * @throws UserAlreadyExistsException If a user with the same email or username already exists.
     */
    @Transactional
    fun register(email: String, username: String, password: String): User {
        val trimmedEmail = email.trim()
        val user = userRepository.findByEmailOrUsername(
            email = trimmedEmail,
            username = username.trim()
        )
        if (user != null) {
            throw UserAlreadyExistsException()
        }

        /** saveAndFlush is used to persist the entity to the database and return the saved entity while working with transactions */
        val savedUser = userRepository.saveAndFlush(
            UserEntity(
                email = trimmedEmail,
                username = username.trim(),
                hashedPassword = passwordEncoder.encode(password)!!
            )
        ).toUser()

        val token = emailVerificationService.createVerificationToken(trimmedEmail)

        /** Publishes a UserEvent.Created event for the newly registered user */
        eventPublisher.publish(
            event = UserEvent.Created(
                userId = savedUser.id,
                email = savedUser.email,
                username = savedUser.username,
                verificationToken = token.token
            )
        )

        return savedUser
    }

    /***
     * Authenticates a user and returns an AuthenticatedUser object.
     * @param email The email of the user.
     * @param password The password of the user.
     * @return An AuthenticatedUser object containing the user, access token, and refresh token.
     * @throws InvalidCredentialsException If the email or password is invalid, or the account is
     *   a social (password-less) account.
     */
    fun login(
        email: String,
        password: String
    ): AuthenticatedUser {
        val user = userRepository.findByEmail(email.trim())
            ?: throw InvalidCredentialsException()

        /** Social accounts have no password — they cannot log in via this endpoint. Treated as
         *  invalid credentials so we don't reveal which accounts are social. */
        val hashedPassword = user.hashedPassword
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(password, hashedPassword)) {
            throw InvalidCredentialsException()
        }

        if (!user.hasVerifiedEmail) {
            val hasActiveToken = emailVerificationService.hasActiveVerificationToken(user)
            if (!hasActiveToken) {
                emailVerificationService.resendVerificationEmail(user.email)
                throw EmailNotVerifiedException(verificationEmailResent = true)
            }
            throw EmailNotVerifiedException(verificationEmailResent = false)
        }

        return authTokenService.issueTokens(user.toUser())
    }

    /**
     * Refreshes an access token using a valid refresh token, rotating the refresh token.
     * @param refreshToken The refresh token to use for refreshing.
     * @return An AuthenticatedUser object containing the refreshed access token and refresh token.
     * @throws InvalidTokenException If the refresh token is invalid.
     * @throws UserNotFoundException If the user associated with the refresh token is not found.
     */
    @Transactional
    fun refresh(refreshToken: String): AuthenticatedUser {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw InvalidTokenException(
                message = "Invalid refresh token"
            )
        }

        val userId = jwtService.getUserIdFromToken(refreshToken)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException()

        val hashed = authTokenService.hashToken(refreshToken)

        refreshTokenRepository.findByUserIdAndHashedToken(
            userId = userId,
            hashedToken = hashed
        ) ?: throw InvalidTokenException("Invalid refresh token")

        refreshTokenRepository.deleteByUserIdAndHashedToken(
            userId = userId,
            hashedToken = hashed
        )

        return authTokenService.issueTokens(user.toUser())
    }

    /***
     * Logs out a user by invalidating their refresh token.
     * @param refreshToken The refresh token to invalidate.
     */
    @Transactional
    fun logout(refreshToken: String) {
        val userId = jwtService.getUserIdFromToken(refreshToken)
        val hashed = authTokenService.hashToken(refreshToken)
        refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashed)
    }
}
