package com.project.chirp.infra.database.mappers

import com.project.chirp.domain.models.Chat
import com.project.chirp.domain.models.ChatMessage
import com.project.chirp.domain.models.ChatParticipant
import com.project.chirp.infra.database.entities.ChatEntity
import com.project.chirp.infra.database.entities.ChatParticipantEntity

/***
 * Converts a ChatEntity to a Chat.
 * @param lastMessage: The last message sent in the chat. This is used here since we need to run 2 queries to fetch the chat and the last message for each chat.
 * We need to wire these together separately, and since we don't want to fire the query into this mapper, we provide the lastMessage as a parameter.
 * @return The Chat.
 */
fun ChatEntity.toChat(lastMessage: ChatMessage? = null): Chat {
    return Chat(
        id = id!!, // we know that id is not null
        participants = participants.map {
            it.toChatParticipant()
        }.toSet(),
        creator = creator.toChatParticipant(),
        lastActivityAt = lastMessage?.createdAt ?: createdAt,
        createdAt = createdAt,
        lastMessage = lastMessage
    )
}

fun ChatParticipantEntity.toChatParticipant(): ChatParticipant {
    return ChatParticipant(
        userId = userId,
        username = username,
        email = email,
        profilePictureUrl = profilePictureUrl
    )
}