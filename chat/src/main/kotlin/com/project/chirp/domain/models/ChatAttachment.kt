package com.project.chirp.domain.models

import com.project.chirp.domain.type.ChatMessageAttachmentId
import java.time.Instant

/***
 * Represents a file attachment on a chat message in the context of the application domain.
 * This is the intermediate model between the persistence layer (ChatMessageAttachmentEntity)
 * and the API layer (ChatAttachmentDto), keeping infrastructure concerns out of the service layer.
 *
 * @param id: The unique identifier of the attachment.
 * @param storageUrl: The publicly accessible URL of the file in Supabase storage.
 * @param mimeType: The MIME type of the file (e.g. image/jpeg).
 * @param originalFileName: The file name as provided by the uploader.
 * @param sizeInBytes: File size in bytes as reported at upload time.
 * @param createdAt: The time the attachment record was created.
 */
data class ChatAttachment(
    val id: ChatMessageAttachmentId,
    val storageUrl: String,
    val mimeType: String,
    val originalFileName: String,
    val sizeInBytes: Long,
    val createdAt: Instant
)