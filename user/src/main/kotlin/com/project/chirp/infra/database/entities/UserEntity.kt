package com.project.chirp.infra.database.entities

import com.project.chirp.domain.model.UserId
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
        Index(name = "idx_users_username", columnList = "username")
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
    @Column(nullable = false)
    var hashedPassword: String,
    @Column(nullable = false)
    var hasVerifiedEmail: Boolean = false,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
    @UpdateTimestamp
    var updatedAt: Instant = Instant.now(),
)