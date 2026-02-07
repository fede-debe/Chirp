package com.project.chirp.service.auth

import com.project.chirp.domain.exception.InvalidCredentialsException
import com.project.chirp.domain.exception.InvalidTokenException
import com.project.chirp.domain.exception.UserAlreadyExistsException
import com.project.chirp.domain.exception.UserNotFoundException
import com.project.chirp.domain.model.AuthenticatedUser
import com.project.chirp.domain.model.User
import com.project.chirp.domain.model.UserId
import com.project.chirp.infra.database.entities.RefreshTokenEntity
import com.project.chirp.infra.database.entities.UserEntity
import com.project.chirp.infra.database.mappers.toUser
import com.project.chirp.infra.database.repositories.RefreshTokenRepository
import com.project.chirp.infra.database.repositories.UserRepository
import com.project.chirp.infra.security.PasswordEncoder
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.*

/***
 * Service for managing authentication-related operations.
 * @see register: Registers a new user.
 * @see login: Authenticates a user and returns an AuthenticatedUser object.
 * @see refresh: Refreshes an access token using a valid refresh token.
 * @see logout: Invalidates a refresh token.
 *
 * @param userRepository: Repository for managing user data.
 * @param passwordEncoder: Encoder for hashing passwords.
 * @param jwtService: Service for generating and validating JWT tokens.
 * @param refreshTokenRepository: Repository for managing refresh tokens.z
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    /***
     * Registers a new user.
     * @param email The email of the user.
     * @param username The username of the user.
     * @param password The password of the user.
     * @return The registered User object.
     * @throws UserAlreadyExistsException If a user with the same email or username already exists.
     */
    fun register(email: String, username: String, password: String): User {
        val user = userRepository.findByEmailOrUsername(
            email = email.trim(),
            username = username.trim()
        )
        /** Handle error with exception */
        if (user != null) {
            throw UserAlreadyExistsException()
        }

        /** calling save would also upsert the user based on primary id */
        val savedUser = userRepository.save(
            UserEntity(
                email = email.trim(),
                username = username.trim(),
                hashedPassword = passwordEncoder.encode(password)!!,

                )
        ).toUser()

        return savedUser
    }

    /***
     * Authenticates a user and returns an AuthenticatedUser object.
     * @param email The email of the user.
     * @param password The password of the user.
     * @return An AuthenticatedUser object containing the user, access token, and refresh token.
     * @throws InvalidCredentialsException If the email or password is invalid.
     * @throws UserNotFoundException If the user is not found.
     */
    fun login(
        email: String,
        password: String
    ): AuthenticatedUser {
        val user = userRepository.findByEmail(email.trim())
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(password, user.hashedPassword)) {
            throw InvalidCredentialsException()
        }

        // TODO: Check for verified email

        /*** Ensure that the user has an ID before proceeding on creating tokens */
        return user.id?.let { userId ->
            val accessToken = jwtService.generateAccessToken(userId)
            val refreshToken = jwtService.generateRefreshToken(userId)

            storeRefreshToken(userId, refreshToken)

            AuthenticatedUser(
                user = user.toUser(),
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        } ?: throw UserNotFoundException()
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * @param refreshToken The refresh token to use for refreshing.
     * @return An AuthenticatedUser object containing the refreshed access token and refresh token.
     * @throws InvalidTokenException If the refresh token is invalid.
     * @throws UserNotFoundException If the user associated with the refresh token is not found.
     *
     * Using @Transactional to ensure atomicity of operations, if all the operations succeed, the transaction is committed and the changes are persisted.
     * If any operation fails, the transaction is rolled back and no changes are persisted.
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

        val hashed = hashToken(refreshToken)

        return user.id?.let { userId ->
            refreshTokenRepository.findByUserIdAndHashedToken(
                userId = userId,
                hashedToken = hashed
            ) ?: throw InvalidTokenException("Invalid refresh token")

            refreshTokenRepository.deleteByUserIdAndHashedToken(
                userId = userId,
                hashedToken = hashed
            )

            val newAccessToken = jwtService.generateAccessToken(userId)
            val newRefreshToken = jwtService.generateRefreshToken(userId)

            storeRefreshToken(userId, newRefreshToken)

            AuthenticatedUser(
                user = user.toUser(),
                accessToken = newAccessToken,
                refreshToken = newRefreshToken
            )
        } ?: throw UserNotFoundException()
    }

    /***
     * Logs out a user by invalidating their refresh token.
     * @param refreshToken The refresh token to invalidate.
     * @throws InvalidTokenException If the refresh token is invalid.
     * @throws UserNotFoundException If the user associated with the refresh token is not found.
     */
    @Transactional
    fun logout(refreshToken: String) {
        val userId = jwtService.getUserIdFromToken(refreshToken)
        val hashed = hashToken(refreshToken)
        refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashed)
    }

    private fun storeRefreshToken(userId: UserId, token: String) {
        val hashed = hashToken(token)
        val expiryMs = jwtService.refreshTokenValidityMs
        val expiresAt = Instant.now().plusMillis(expiryMs)

        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId = userId,
                expiresAt = expiresAt,
                hashedToken = hashed
            )
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.encodeToByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}