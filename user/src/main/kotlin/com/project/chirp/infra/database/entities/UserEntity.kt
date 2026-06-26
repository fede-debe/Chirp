package com.project.chirp.infra.database.entities

import com.project.chirp.domain.model.AuthProvider
import com.project.chirp.domain.type.UserId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/** we don't use data class here because in that way we can't run into
 * any problems that could originate from equals override that JPA
 * does not expect
 *
 * database indexes are extra info that is saved for a certain table that
 * let us query certain fields faster */

@Entity
@Table(
    name = "users",
    schema = "user_service",
    indexes = [
        Index(name = "idx_users_email", columnList = "email"),
        Index(name = "idx_users_username", columnList = "username"),
        // Resolve a returning social user by their stable provider id. Unique so a provider
        // account links to at most one user. (provider_id is NULL for email accounts, and
        // Postgres treats NULLs as distinct, so many email rows coexist under this index.)
        Index(
            name = "idx_users_auth_provider_provider_id",
            columnList = "auth_provider,provider_id",
            unique = true
        ),
    ]
)

class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UserId? = null,
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = false, unique = true)
    var username: String,
    // Nullable: social (Google/Apple) accounts are password-less.
    @Column(nullable = true)
    var hashedPassword: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    var authProvider: AuthProvider = AuthProvider.EMAIL,
    // The provider's stable user id (`sub`); NULL for email/password accounts.
    @Column(name = "provider_id")
    var providerId: String? = null,
    @Column(nullable = false)
    var hasVerifiedEmail: Boolean = false,
    // Whether the chat-module participant mirror has been provisioned for this user. Set once —
    // on email verification or the first social sign-in — so UserEvent.Verified is published only
    // the first time, not on every subsequent login.
    @Column(name = "chat_participant_provisioned", nullable = false, columnDefinition = "boolean not null default false")
    var chatParticipantProvisioned: Boolean = false,
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    var typingIndicatorsEnabled: Boolean = true,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
    @UpdateTimestamp
    var updatedAt: Instant = Instant.now(),
)