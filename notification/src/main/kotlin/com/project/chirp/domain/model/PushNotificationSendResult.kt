package com.project.chirp.domain.model

/***
 * Represents the result of sending a push notification.
 *
 * @param succeeded: List of device tokens for which the notification was successfully sent.
 * @param temporaryFailures: List of device tokens for which the notification failed temporarily.
 * @param permanentFailures: List of device tokens for which the notification failed permanently.
 */
data class PushNotificationSendResult(
    val succeeded: List<DeviceToken>,
    val temporaryFailures: List<DeviceToken>,
    val permanentFailures: List<DeviceToken>,
)