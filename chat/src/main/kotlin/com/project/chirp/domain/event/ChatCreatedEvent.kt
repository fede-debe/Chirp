package com.project.chirp.domain.event

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId

/***
 * Event triggered when a chat is created.
 * @param chatId: The unique identifier for the chat.
 * @param participantIds: The unique identifiers for the participants in the chat.
 * */
data class ChatCreatedEvent(
    val chatId: ChatId,
    val participantIds: List<UserId>
)