package com.project.chirp.api.dto

import com.project.chirp.domain.type.UserId

/***
 * DTO for chat participants.
 * @param userId The ID of the user.
 * @param username The username of the user.
 * @param email The email of the user.
 * @param profilePictureUrl The URL of the user's profile picture.
 *
 * @see ChatDto
 */
data class ChatParticipantDto(
    val userId: UserId,
    val username: String,
    val email: String,
    val profilePictureUrl: String?
)