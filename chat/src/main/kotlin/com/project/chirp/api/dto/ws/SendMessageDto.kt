package com.project.chirp.api.dto.ws

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import com.project.chirp.service.AttachmentInput

/***
 * DTO for sending a new chat message.
 * @param chatId The ID of the chat.
 * @param content The content of the chat message.
 * @param messageId The ID of the chat message (optional).
 * @param attachments The attachments to include with the message (optional).
 * */
data class SendMessageDto(
    val chatId: ChatId,
    val content: String,
    val messageId: ChatMessageId? = null,
    val attachments: List<AttachmentInput>? = null
)