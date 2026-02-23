package com.project.chirp.infra.database.entities

import com.project.chirp.domain.type.ChatId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/***
 * Represents a chat in the database.
 * @param id: The unique identifier for the chat.
 * @param creator: The participant who created the chat.
 * @param participants: The participants in the chat.
 * @param createdAt: The creation time of the chat.
 */
@Entity
@Table(
    name = "chats",
    schema = "chat_service"
)
class ChatEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: ChatId? = null,
    @ManyToOne(fetch = FetchType.LAZY) // one creator can be associated with multiple chats
    @JoinColumn(
        name = "creator_id",
        nullable = false
    )
    var creator: ChatParticipantEntity,
    @ManyToMany(fetch = FetchType.LAZY) // participants can be associated with multiple chats
    // SQL doesn't work with a list, with many-to-many requires a separate table that keeps
    // combinations of chat_id and user_id (primary keys).
    // we need to know which participants belongs to which chat and with hibernate we can use @JoinTable to achieve this.
    @JoinTable(
        name = "chat_participants_cross_ref", // cross_ref is a naming convention for cross-reference tables.
        schema = "chat_service",
        joinColumns = [JoinColumn(name = "chat_id")], // based on which column these entries will be joined
        inverseJoinColumns = [JoinColumn(name = "user_id")], // counterpart of joinColumns, we have a clear unique combination of chat_id and user_id
        // since now we have an extra table, we can create indexes on it to improve query performance for this table for chats and participants.
        indexes = [
            // Answers efficiently:
            // Who is in chat X?
            Index(
                name = "idx_chat_participant_chat_id_user_id",
                columnList = "chat_id,user_id", // order here matters, chat_id comes first (here we check the chat, and how many participants are in it)
                unique = true // make sure user can only be in a chat once
            ),
            // Answers efficiently:
            // What chats is user X in?
            Index(
                name = "idx_chat_participant_user_id_chat_id",
                columnList = "user_id,chat_id", // take a look at a user (user_id), and know which multiple chats that user is in by looking at the chat_id
                unique = true
            ),
        ]
    )
    var participants: MutableSet<ChatParticipantEntity> = mutableSetOf(),
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)