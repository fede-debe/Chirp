package com.project.chirp.infra.database.entities

import com.project.chirp.domain.type.UserId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/***
 * Represents a refresh token entity in the database.
 * @param id The unique identifier for the refresh token.
 * @param userId The user ID associated with the refresh token.
 * @param expiresAt The expiration time of the refresh token.
 * @param hashedToken The hashed value of the refresh token. We are storing the hashed value to prevent token replay attacks.
 * @param createdAt The creation time of the refresh token.
 *
 * indexes
 * - idx_refresh_tokens_user_id: Index on the user_id column for faster queries.
 * - idx_refresh_tokens_user_token: Index on the user_id and hashed_token columns for faster queries.
 */
@Entity
@Table(
    name = "refresh_tokens",
    schema = "user_service",
    indexes = [
        Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_refresh_tokens_user_token", columnList = "user_id,hashed_token"),
    ]
)
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false)
    var userId: UserId,
    @Column(nullable = false)
    var expiresAt: Instant,
    @Column(nullable = false)
    var hashedToken: String,
    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)