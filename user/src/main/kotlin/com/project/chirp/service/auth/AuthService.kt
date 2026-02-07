package com.project.chirp.service.auth

import com.project.chirp.domain.exception.InvalidCredentialsException
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
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.*

/***
 * Service for managing authentication-related operations.
 * @see register: Registers a new user.
 * @see login: Authenticates a user and returns an AuthenticatedUser object.
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