package com.project.chirp.infra.mappers

import com.project.chirp.domain.model.DeviceToken
import com.project.chirp.infra.database.DeviceTokenEntity

/***
 * Converts a device token entity to its corresponding domain model.
 */
fun DeviceTokenEntity.toDeviceToken(): DeviceToken {
    return DeviceToken(
        userId = userId,
        token = token,
        platform = platform.toPlatform(),
        createdAt = createdAt,
        id = id
    )
}