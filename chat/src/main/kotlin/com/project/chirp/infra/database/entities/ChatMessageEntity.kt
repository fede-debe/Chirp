package com.project.chirp.infra.database.entities

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

/***
 * Represents a chat message in the database.
 * @param id: The unique identifier for the chat message.
 * @param content: The content of the chat message.
 * @param chatId: The unique identifier for the chat associated with the message.
 * @param chat: The chat associated with the message.
 * @param sender: The participant who sent the message.
 * @param createdAt: The creation time of the chat message.
 */
@Entity
@Table(
    name = "chat_messages",
    schema = "chat_service",

    // needed for fetch messages that belong to a chat ordered by creation time
    indexes = [
        Index(
            name = "idx_chat_message_chat_id_created_at",
            columnList = "chat_id,created_at DESC"
        )
    ]
)
class ChatMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: ChatMessageId? = null,
    @Column(nullable = false)
    var content: String,
    @Column(
        name = "chat_id",
        nullable = false,
        updatable = false
    )
    var chatId: ChatId, // we store the chatId to efficiently retrieve the value instead of using the other chat parameter,
    // chat is declared as LAZY, and it's not fetched by default and only the moment we try to access it.
    @ManyToOne(fetch = FetchType.LAZY) // one chat can have multiple messages, one message can only belong to one chat
    @JoinColumn(
        name = "chat_id",
        nullable = false,
        insertable = false,
        updatable = false
    )
    @OnDelete(action = OnDeleteAction.CASCADE) // if a chat is deleted, all its messages will also be deleted
    var chat: ChatEntity? = null, // default value is null because of Java/Kotlin limitation, if we don't give a default value, the moment we create the ChatMessageEntity, it will expect an actual chat
    @ManyToOne(fetch = FetchType.EAGER) // we set this to EAGER because we wouldn't need the LEFT JOIN FETCH for sender
    @JoinColumn(
        name = "sender_id",
        nullable = false,
        insertable = false,
        updatable = false
    )
    var sender: ChatParticipantEntity,
    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)