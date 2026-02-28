package com.project.chirp.domain.event

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId

/***
 * This is an application event triggered when a chat message is deleted.
 * The events from this package will not be used by RabbitMQ.
 * Event triggered when a chat message is deleted.
 *
 * The class contains the data that we need and want to broadcast to the clients.
 * It provides information to know which message was deleted.
 *
 * These application events will be relevant only for those users who are actively
 * connected to a chat. If user is offline or app is not opened, this change will
 * be refreshed next time the user opens the app. For those users who are actively
 * connected to the Websocket, we can stream these changes in real time.
 */
data class MessageDeletedEvent(
    val chatId: ChatId,
    val messageId: ChatMessageId,
)