package com.project.chirp.api.dto

import jakarta.validation.constraints.NotBlank

/***
 * Request to register a device token.
 * @param token The device token.
 * @param platform The platform of the device.
 */
data class RegisterDeviceRequest(
    @field:NotBlank
    val token: String,
    val platform: PlatformDto
)

enum class PlatformDto {
    ANDROID, IOS
}