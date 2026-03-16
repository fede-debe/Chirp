package com.project.chirp.api.dto.ws

import com.project.chirp.domain.type.ChatId

data class TypingEventDto(
    val chatId: ChatId
)