package com.project.chirp.chat.domain.models

import com.project.chirp.domain.type.ChatId
import java.time.Instant

/***
 * Represents a chat in the context of the app.
 *
 * @param id: The unique identifier for the chat.
 * @param participants: The participants in the chat.
 * @param lastMessage: The last message sent in the chat.
 * We need it to display a preview of the chat in the chat list.
 * @param creator: The participant who created the chat.
 * @param lastActivityAt: The time when the chat was last active.
 * @param createdAt: The time when the chat was created.
 */
data class Chat(
    val id: ChatId,
    val participants: Set<ChatParticipant>,
    val lastMessage: ChatMessage?,
    val creator: ChatParticipant,
    val lastActivityAt: Instant,
    val createdAt: Instant
)