package com.project.chirp.domain.model

import com.project.chirp.domain.type.UserId
import java.time.Instant

/***
 * Represents a device token for push notifications.
 * @param id: Unique identifier for the device token.
 * @param userId: Unique identifier for the user associated with the device token.
 * @param token: The actual device token.
 * @param platform: The platform of the device (Android or iOS).
 * @param createdAt: The creation time of the device token.
 */
data class DeviceToken(
    val id: Long,
    val userId: UserId,
    val token: String,
    val platform: Platform,
    val createdAt: Instant = Instant.now(),
) {
    enum class Platform {
        ANDROID, IOS
    }
}