package com.project.chirp.service

import com.project.chirp.domain.model.AuthenticatedUser
import com.project.chirp.domain.model.User
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.RefreshTokenEntity
import com.project.chirp.infra.database.repositories.RefreshTokenRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.*

/***
 * The single place that issues this backend's own access + refresh tokens and persists the
 * (hashed) refresh token. Extracted so email/password login, refresh, and social sign-in all go
 * through the EXACT same token-issuing path — there is no parallel auth stack.
 *
 * Refresh tokens are SHA-256 hashed (Base64) before storage; the raw token is never persisted.
 *
 * @see issueTokens Generates a fresh access + refresh pair and stores the refresh hash.
 * @see hashToken SHA-256 + Base64 of a refresh token, for lookup/rotation/deletion.
 */
@Service
class AuthTokenService(
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    fun issueTokens(user: User): AuthenticatedUser {
        val accessToken = jwtService.generateAccessToken(user.id)
        val refreshToken = jwtService.generateRefreshToken(user.id)
        storeRefreshToken(user.id, refreshToken)
        return AuthenticatedUser(
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.encodeToByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    private fun storeRefreshToken(userId: UserId, token: String) {
        val expiresAt = Instant.now().plusMillis(jwtService.refreshTokenValidityMs)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId = userId,
                expiresAt = expiresAt,
                hashedToken = hashToken(token),
            )
        )
    }
}
