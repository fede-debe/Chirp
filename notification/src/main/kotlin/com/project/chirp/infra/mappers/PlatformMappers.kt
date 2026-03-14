package com.project.chirp.infra.mappers

import com.project.chirp.domain.model.DeviceToken
import com.project.chirp.infra.database.PlatformEntity

/***
 * Converts a device token platform to its corresponding database entity.
 */
fun DeviceToken.Platform.toPlatformEntity(): PlatformEntity {
    return when (this) {
        DeviceToken.Platform.ANDROID -> PlatformEntity.ANDROID
        DeviceToken.Platform.IOS -> PlatformEntity.IOS
    }
}

/***
 * Converts a database entity platform to its corresponding device token platform.
 */
fun PlatformEntity.toPlatform(): DeviceToken.Platform {
    return when (this) {
        PlatformEntity.ANDROID -> DeviceToken.Platform.ANDROID
        PlatformEntity.IOS -> DeviceToken.Platform.IOS
    }
}