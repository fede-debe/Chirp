package com.project.chirp.api.dto

import com.project.chirp.domain.type.UserId
import java.time.Instant

/***
 * DTO for device tokens.
 * @param userId The ID of the user associated with the device token.
 * @param token The actual device token.
 * @param createdAt The creation time of the device token.
 *
 */
data class DeviceTokenDto(
    val userId: UserId,
    val token: String,
    val createdAt: Instant
)