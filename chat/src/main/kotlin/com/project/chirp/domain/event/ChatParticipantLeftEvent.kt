package com.project.chirp.domain.event

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId

data class ChatParticipantLeftEvent(
    val chatId: ChatId,
    val userId: UserId
)