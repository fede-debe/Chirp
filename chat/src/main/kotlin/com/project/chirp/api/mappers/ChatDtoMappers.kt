package com.project.chirp.api.mappers

import com.project.chirp.api.dto.ChatAttachmentDto
import com.project.chirp.api.dto.ChatDto
import com.project.chirp.api.dto.ChatMessageDto
import com.project.chirp.api.dto.ChatParticipantDto
import com.project.chirp.domain.models.Chat
import com.project.chirp.domain.models.ChatAttachment
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

/***
 * Converts a ChatAttachment domain model to its DTO representation for API responses.
 */
fun ChatAttachment.toChatAttachmentDto(): ChatAttachmentDto {
    return ChatAttachmentDto(
        id = id,
        storageUrl = storageUrl,
        mimeType = mimeType,
        originalFileName = originalFileName,
        sizeInBytes = sizeInBytes,
        durationInSeconds = durationInSeconds,
        createdAt = createdAt
    )
}

/***
 * Converts a ChatMessage domain model to its DTO representation for API responses.
 * Attachments are mapped using toChatAttachmentDto() and will be an empty list when the
 * underlying entity was loaded without fetching attachments (e.g. for last-message previews).
 */
fun ChatMessage.toChatMessageDto(): ChatMessageDto {
    return ChatMessageDto(
        id = id,
        chatId = chatId,
        content = content,
        createdAt = createdAt,
        senderId = sender.userId,
        attachments = attachments.map { it.toChatAttachmentDto() }
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