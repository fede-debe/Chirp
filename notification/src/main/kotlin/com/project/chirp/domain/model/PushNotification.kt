package com.project.chirp.domain.model

import com.project.chirp.domain.type.ChatId
import java.util.*

/***
 * Represents a push notification to be sent to one or more devices.
 *
 * @param id: Unique identifier for the notification.
 * @param title: Title of the notification.
 * @param recipients: List of device tokens to send the notification to.
 * @param message: Body of the notification.
 * @param chatId: Unique identifier for the chat associated with the notification.
 * @param data: Additional data to be sent with the notification from the client.
 */
data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val recipients: List<DeviceToken>,
    val message: String,
    val chatId: ChatId,
    val data: Map<String, String>
)