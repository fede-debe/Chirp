package com.project.chirp.api.dto.ws

import com.project.chirp.domain.type.ChatId

/***
 * This is a data class representing a chat participants changed event.
 * It contains the chat ID.
 * This is the fastest way to notify clients about changes in chat participants, after that
 * a normal rest API call can be made to fetch the updated chat details. This keeps the
 * payload small and efficient since the chat could have multiple updates at the same time.
 * */
data class ChatParticipantsChangedDto(
    val chatId: ChatId
)