package com.project.chirp.api.dto.ws

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId

data class DeleteMessageDto(
    val chatId: ChatId,
    val messageId: ChatMessageId
)