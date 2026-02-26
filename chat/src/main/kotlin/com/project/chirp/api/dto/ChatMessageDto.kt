package com.project.chirp.api.dto

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import com.project.chirp.domain.type.UserId
import java.time.Instant

/***
 * DTO for chat messages.
 * @param id The ID of the chat message.
 * @param chatId The ID of the chat.
 * @param content The content of the chat message.
 * @param createdAt The creation time of the chat message.
 * @param senderId The ID of the sender of the chat message.
 *
 * @see ChatDto
 */
data class ChatMessageDto(
    val id: ChatMessageId,
    val chatId: ChatId,
    val content: String,
    val createdAt: Instant,
    val senderId: UserId
)