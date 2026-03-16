package com.project.chirp.infra.database.entities

import com.project.chirp.domain.type.ChatMessageAttachmentId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

/***
 * Represents a file attachment associated with a chat message in the database.
 *
 * @param id: Auto-generated UUID primary key. Nullable because Hibernate sets it after INSERT.
 * @param chatMessage: The parent message this attachment belongs to. Declared as LAZY because we
 *   never need to navigate back from an attachment to its message — the message always owns the
 *   relationship. @OnDelete CASCADE delegates physical row deletion to the database rather than
 *   requiring Hibernate to load the entity first, keeping delete operations efficient.
 * @param storageUrl: The publicly accessible URL of the file in Supabase storage. Stored here
 *   so the client can render attachments without an extra storage API call.
 * @param mimeType: The MIME type of the uploaded file (e.g. image/jpeg). Stored alongside the
 *   URL so the client knows how to render the attachment without inspecting the file itself.
 * @param originalFileName: The file name as provided by the uploader. Stored for display purposes
 *   and to allow future download-with-original-name scenarios.
 * @param sizeInBytes: File size in bytes as reported at upload time. Stored so the client can
 *   display file size without fetching the object from storage.
 * @param createdAt: Timestamp set automatically by Hibernate at INSERT time via @CreationTimestamp.
 *   Not settable by the application layer.
 *
 * The index on message_id is added because the most common access pattern is fetching all
 * attachments for a given message — without it each JOIN FETCH would result in a full table scan.
 */
@Entity
@Table(
    name = "chat_message_attachments",
    schema = "chat_service",
    indexes = [
        Index(
            name = "idx_chat_message_attachment_message_id",
            columnList = "message_id"
        )
    ]
)
class ChatMessageAttachmentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: ChatMessageAttachmentId? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var chatMessage: ChatMessageEntity,

    @Column(nullable = false)
    var storageUrl: String,

    @Column(nullable = false)
    var mimeType: String,

    @Column(nullable = false)
    var originalFileName: String,

    @Column(nullable = false)
    var sizeInBytes: Long,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)
