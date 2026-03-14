package com.project.chirp.infra.database

import com.project.chirp.domain.type.UserId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/***
 * Represents a device token in the database.
 * @param id: The unique identifier for the device token.
 * @param userId: The user associated with the device token.
 * @param token: The device token.
 * @param platform: The platform of the device.
 * @param createdAt: The creation time of the device token.
 *
 * indexes: token and user_id
 */
@Entity
@Table(
    name = "device_tokens",
    schema = "notification_service",
    indexes = [
        Index(name = "idx_device_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_device_tokens_token", columnList = "token", unique = true),
    ]
)
class DeviceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-incrementing primary key
    var id: Long = 0,
    @Column(nullable = false)
    var userId: UserId,
    @Column(nullable = false)
    var token: String,
    @Enumerated(EnumType.STRING) // serialized as a string in the database, parsed from JPA to enum
    @Column(nullable = false)
    var platform: PlatformEntity,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)