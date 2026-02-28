package com.project.chirp.domain.events.chat

import com.project.chirp.domain.events.ChirpEvent
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId
import java.time.Instant
import java.util.*

/***
 * This is a sealed class for chat-related events.
 * @see NewMessage Represents a new chat message event.
 */
sealed class ChatEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val exchange: String = ChatEventConstants.CHAT_EXCHANGE,
    override val occurredAt: Instant = Instant.now(),
) : ChirpEvent {

    /***
     * Represents a new chat message event.
     * These values are all the info we need in the notification service to send
     * a proper push notification.
     * @param senderId: The ID of the sender of the message.
     * @param senderUsername: The username of the sender of the message.
     * @param recipientIds: The IDs of the recipients of the message.
     * @param chatId: The ID of the chat where the message was sent.
     * @param message: The content of the message.
     */
    data class NewMessage(
        val senderId: UserId,
        val senderUsername: String,
        val recipientIds: Set<UserId>,
        val chatId: ChatId,
        val message: String,
        override val eventKey: String = ChatEventConstants.CHAT_NEW_MESSAGE
    ) : ChatEvent(), ChirpEvent
}