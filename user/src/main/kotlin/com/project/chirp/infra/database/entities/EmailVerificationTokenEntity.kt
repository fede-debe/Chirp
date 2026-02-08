package com.project.chirp.infra.database.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/***
 * Represents an email verification token entity in the database.
 * @param id The unique identifier for the email verification token, server facing using Long.
 * @param expiresAt The expiration time of the email verification token.
 * @param token The value of the email verification token.
 * @param createdAt The creation time of the email verification token.
 * @param usedAt The time when the email verification token was used, or null if it has not been used.
 * @param user The user associated with the email verification token. Combining the user entity with the email verification token entity.
 * SQL allow us to link specific tables with a so-called relation.
 *
 * One token can belong to only one user, but one user can have multiple email verification tokens (one-to-many relationship).
 * This is why we are using @ManyToOne annotation, the FetchType.LAZY ensures that the user entity is not loaded until it is accessed.
 * And the @JoinColumn(name = "user_id", nullable = false) ensures that the user_id column is not nullable, and it is joined with the EmailVerificationTokenEntity.
 *
 *
 * @see EmailVerificationTokenEntity
 */
@Entity
@Table(
    name = "email_verification_tokens",
    schema = "user_service",
)
class EmailVerificationTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, unique = true)
    var token: String,
    @Column(nullable = false)
    var expiresAt: Instant,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
    @Column
    var usedAt: Instant?,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)