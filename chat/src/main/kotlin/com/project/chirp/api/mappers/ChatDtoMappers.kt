package com.project.chirp.api.mappers

import com.project.chirp.api.dto.ChatDto
import com.project.chirp.api.dto.ChatMessageDto
import com.project.chirp.api.dto.ChatParticipantDto
import com.project.chirp.domain.models.Chat
import com.project.chirp.domain.models.ChatMessage
import com.project.chirp.domain.models.ChatParticipant

/***
 * Converts a Chat to a ChatDto.
 */
fun Chat.toChatDto(): ChatDto {
    return ChatDto(
        id = id,
        participants = participants.map {
            it.toChatParticipantDto()
        },
        lastActivityAt = lastActivityAt,
        lastMessage = lastMessage?.toChatMessageDto(),
        creator = creator.toChatParticipantDto()
    )
}

fun ChatMessage.toChatMessageDto(): ChatMessageDto {
    return ChatMessageDto(
        id = id,
        chatId = chatId,
        content = content,
        createdAt = createdAt,
        senderId = sender.userId
    )
}

fun ChatParticipant.toChatParticipantDto(): ChatParticipantDto {
    return ChatParticipantDto(
        userId = userId,
        username = username,
        email = email,
        profilePictureUrl = profilePictureUrl
    )
}