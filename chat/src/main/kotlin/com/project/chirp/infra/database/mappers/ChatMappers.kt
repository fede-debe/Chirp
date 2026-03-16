package com.project.chirp.infra.database.mappers

import com.project.chirp.domain.models.Chat
import com.project.chirp.domain.models.ChatAttachment
import com.project.chirp.domain.models.ChatMessage
import com.project.chirp.domain.models.ChatParticipant
import com.project.chirp.infra.database.entities.ChatEntity
import com.project.chirp.infra.database.entities.ChatMessageAttachmentEntity
import com.project.chirp.infra.database.entities.ChatMessageEntity
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

fun ChatParticipant.toChatParticipantEntity(): ChatParticipantEntity {
    return ChatParticipantEntity(
        userId = userId,
        username = username,
        email = email,
        profilePictureUrl = profilePictureUrl
    )
}

/***
 * Converts a ChatMessageAttachmentEntity to its domain model representation.
 */
fun ChatMessageAttachmentEntity.toChatAttachment(): ChatAttachment {
    return ChatAttachment(
        id = id!!,
        storageUrl = storageUrl,
        mimeType = mimeType,
        originalFileName = originalFileName,
        sizeInBytes = sizeInBytes,
        durationInSeconds = durationInSeconds,
        createdAt = createdAt
    )
}

/***
 * Converts a ChatMessageEntity to its domain model representation.
 * Attachments are mapped inline via toChatAttachment(). They are available without an additional
 * query only when the entity was loaded with LEFT JOIN FETCH m.attachments, as done in
 * ChatMessageRepository. In all other cases the collection is empty (LAZY default).
 */
fun ChatMessageEntity.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id!!,
        chatId = chatId,
        sender = sender.toChatParticipant(),
        content = content,
        createdAt = createdAt,
        attachments = attachments.map { it.toChatAttachment() }
    )
}