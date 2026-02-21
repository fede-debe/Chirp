package com.project.chirp.chat.domain.models

import com.project.chirp.domain.type.UserId

/***
 * Represents a participant in a chat.
 * It is the only class that don't depend on other domain models.
 * The ChatParticipant can be thought as the User from the User module.
 * But we don't want to have any kind of knowledge about the User db schema and have our own chat service schema.
 * If you have a setup of distributed services, which each service has its own isolated database instance,
 * then it's common that each service manages its own data.
 *
 * As long as the User is logged in and verified, we want to create them as ChatParticipant.
 *
 * @param userId: The unique identifier for the user.
 * @param username: The username of the user.
 * @param email: The email of the user.
 * @param profilePictureUrl: The URL of the user's profile picture.
 */
data class ChatParticipant(
    val userId: UserId,
    val username: String,
    val email: String,
    val profilePictureUrl: String?
)