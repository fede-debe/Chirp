package com.project.chirp.service

import com.project.chirp.domain.exception.InvalidTokenException
import com.project.chirp.domain.type.UserId
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import kotlin.io.encoding.Base64

/** Param values come from application.yml */
@Service
class JwtService(
    @param:Value("\${jwt.secret}") private val secretBase64: String,
    @param:Value("\${jwt.expiration-minutes}") private val expirationMinutes: Int,
) {

    /***
     * Secret key for signing JWT tokens.
     */
    private val secretKey = Keys.hmacShaKeyFor(
        Base64.Default.decode(secretBase64)
    )

    /***
     * Access token validity is the specified expiration time in minutes.
     */
    private val accessTokenValidityMs = expirationMinutes * 60 * 1000L

    /***
     * Refresh token validity is 30 days
     */
    val refreshTokenValidityMs = 30 * 24 * 60 * 60 * 1000L

    /***
     * Generates an access token for the given user ID.
     * @param userId The user ID to be encoded in the JWT token.
     * @return The generated JWT access token.
     */
    fun generateAccessToken(userId: UserId): String {
        return generateToken(
            userId = userId,
            type = "access",
            expiry = accessTokenValidityMs
        )
    }

    /***
     * @param userId: The user ID to be encoded in the JWT refresh token.
     * @return The generated JWT refresh token.
     */
    fun generateRefreshToken(userId: UserId): String {
        return generateToken(
            userId = userId,
            type = "refresh",
            expiry = refreshTokenValidityMs
        )
    }

    /***
     * Validates an access token.
     * @param token The access token to validate.
     * @return True if the token is valid, false otherwise.
     */
    fun validateAccessToken(token: String): Boolean {
        val claims = parseAllClaims(token) ?: return false
        val tokenType = claims["type"] as? String ?: return false
        return tokenType == "access"
    }

    /***
     * Validates a refresh token.
     * @param token The refresh token to validate.
     * @return True if the token is valid, false otherwise.
     */
    fun validateRefreshToken(token: String): Boolean {
        val claims = parseAllClaims(token) ?: return false
        val tokenType = claims["type"] as? String ?: return false
        return tokenType == "refresh"
    }

    /**
     * @param token The JWT token to parse.
     * @return The user ID encoded in the JWT token.
     * @throws com.project.chirp.domain.exception.InvalidTokenException if the token is not valid.
     */
    fun getUserIdFromToken(token: String): UserId {
        val claims = parseAllClaims(token) ?: throw InvalidTokenException(
            message = "The attached JWT token is not valid"
        )
        return UUID.fromString(claims.subject)
    }

    /**
     * @param userId: The user ID to be encoded in the JWT token.
     * @param type: The type of the token, either "access" or "refresh".
     * @param expiry: The expiry time of the token in milliseconds.
     * @return The generated JWT token.
     */
    private fun generateToken(
        userId: UserId,
        type: String,
        expiry: Long
    ): String {
        val now = Date()
        val expiryDate = Date(now.time + expiry)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", type)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    /**
     * Parses a JWT token and returns its claims.
     * @param token The JWT token to parse.
     * @return The claims of the JWT token, or null if the token is invalid.
     */
    private fun parseAllClaims(token: String): Claims? {
        val rawToken = if (token.startsWith("Bearer ")) {
            token.removePrefix("Bearer ")
        } else token

        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(rawToken)
                .payload
        } catch (e: Exception) {
            null
        }
    }
}