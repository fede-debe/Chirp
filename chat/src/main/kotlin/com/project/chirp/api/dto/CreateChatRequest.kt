package com.project.chirp.api.dto

import com.project.chirp.domain.type.UserId
import jakarta.validation.constraints.Size

/***
 * DTO for creating a chat.
 * @param otherUserIds The IDs of the other participants in the chat.
 * Validates that there are at least 2 unique participants.
 *
 * @see ChatDto
 */
data class CreateChatRequest(
    @field:Size(
        min = 1,
        message = "Chats must have at least 2 unique participants"
    )
    val otherUserIds: List<UserId>
)