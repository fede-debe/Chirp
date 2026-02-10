package com.project.chirp.infra.database.entities

import com.project.chirp.infra.security.TokenGenerator
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/***
 * Represents a password reset token entity in the database.
 * @param id The unique identifier for the password reset token.
 * @param token The token value for password reset.
 * @param user The user associated with the password reset token.
 * @param expiresAt The expiration time of the password reset token.
 * @param usedAt The time when the password reset token was used.
 * @param createdAt The creation time of the password reset token.
 *
 */
@Entity
@Table(
    name = "password_reset_tokens",
    schema = "user_service",
    indexes = [
        Index(name = "idx_password_reset_token_token", columnList = "token")
    ]
)
class PasswordResetTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, unique = true)
    var token: String = TokenGenerator.generateSecureToken(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
    @Column(nullable = false)
    var expiresAt: Instant,
    @Column(nullable = true)
    var usedAt: Instant? = null,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)