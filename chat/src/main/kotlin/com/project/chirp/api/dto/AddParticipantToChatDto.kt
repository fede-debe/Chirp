package com.project.chirp.api.dto

import com.project.chirp.domain.type.UserId
import jakarta.validation.constraints.Size

/***
 * DTO for adding participants to a chat.
 * @param userIds The IDs of the participants to add.
 * Validates that there is at least one participant.
 *
 * @see ChatDto
 */
data class AddParticipantToChatDto(
    @field:Size(min = 1)
    val userIds: List<UserId>
)