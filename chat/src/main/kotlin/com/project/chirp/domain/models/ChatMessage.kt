package com.project.chirp.domain.models

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import java.time.Instant

/***
 * Represents a message in a chat in the cotext of the app.
 *
 * @param id: The unique identifier for the chat message.
 * @param chatId: The unique identifier for the chat.
 * We don't want the entire chat content in a single message.
 * @param sender: The participant who sent the message.
 * @param content: The content of the message.
 * @param createdAt: The time when the message was created.
 * @param attachments: The file attachments associated with the message. Defaults to an empty list
 *   so that callers that do not need attachments (e.g. last-message previews in chat lists) are
 *   not forced to supply them.
 */
data class ChatMessage(
    val id: ChatMessageId,
    val chatId: ChatId,
    val sender: ChatParticipant,
    val content: String,
    val createdAt: Instant,
    val attachments: List<ChatAttachment> = emptyList()
)
