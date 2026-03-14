package com.project.chirp.api.controllers

import com.project.chirp.api.dto.DeviceTokenDto
import com.project.chirp.api.dto.RegisterDeviceRequest
import com.project.chirp.api.mappers.toDeviceTokenDto
import com.project.chirp.api.mappers.toPlatformDto
import com.project.chirp.api.util.requestUserId
import com.project.chirp.service.PushNotificationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/***
 * Controller for device token-related operations.
 * @see registerDeviceToken Registers a device token.
 * @see unregisterDeviceToken Unregisters a device token.
 *
 * @author fede-debe
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/notification")
class DeviceTokenController(private val pushNotificationService: PushNotificationService) {

    @PostMapping("/register")
    fun registerDeviceToken(
        @Valid @RequestBody body: RegisterDeviceRequest
    ): DeviceTokenDto {
        return pushNotificationService.registerDevice(
            userId = requestUserId,
            token = body.token,
            platform = body.platform.toPlatformDto()
        ).toDeviceTokenDto()
    }

    @DeleteMapping("/{token}")
    fun unregisterDeviceToken(
        @PathVariable("token") token: String
    ) {
        pushNotificationService.unregisterDevice(token)
    }
}