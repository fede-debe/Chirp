package com.project.chirp.infra.database.entities

import com.project.chirp.domain.type.UserId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/***
 * Represents a chat participant in the database.
 * @param userId: The unique identifier for the chat participant.
 * Verified event we will send when an email is verified, this will be
 * sent of via Rabbit MQ, will be received in the chat module. As a result of
 * a user being verified, we will create an instance of the ChatParticipantEntity
 * with the same userId from the user table.
 * @param username: The username of the chat participant.
 * @param email: The email of the chat participant.
 * @param profilePictureUrl: The URL of the profile picture of the chat participant.
 * @param createdAt: The creation time of the chat participant.
 *
 * indexes are necessary for efficient querying.
 */
@Entity
@Table(
    name = "chat_participants",
    schema = "chat_service",
    indexes = [
        Index(name = "idx_chat_participant_username", columnList = "username"),
        Index(name = "idx_chat_participant_email", columnList = "email"),
    ]
)
class ChatParticipantEntity(
    @Id
    var userId: UserId,
    @Column(nullable = false, unique = true)
    var username: String,
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = true)
    var profilePictureUrl: String? = null,
    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)