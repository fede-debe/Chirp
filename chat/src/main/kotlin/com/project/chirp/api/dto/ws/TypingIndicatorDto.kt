package com.project.chirp.api.dto.ws

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId

data class TypingIndicatorDto(
    val chatId: ChatId,
    val userId: UserId,
    val username: String,
    val isTyping: Boolean
)
