package com.project.chirp.api.dto

import com.project.chirp.domain.type.ChatMessageAttachmentId
import java.time.Instant

/***
 * DTO for a file attachment on a chat message, returned to clients as part of ChatMessageDto.
 *
 * @param id: The unique identifier of the attachment.
 * @param storageUrl: The publicly accessible URL of the file. The client uses this directly to
 *   render or download the attachment.
 * @param mimeType: The MIME type of the file, so the client knows how to display it without
 *   inspecting the file content.
 * @param originalFileName: The file name as supplied by the uploader, used for display and download.
 * @param sizeInBytes: File size in bytes, displayed to the user before download.
 * @param createdAt: The time the attachment was created on the server.
 */
data class ChatAttachmentDto(
    val id: ChatMessageAttachmentId,
    val storageUrl: String,
    val mimeType: String,
    val originalFileName: String,
    val sizeInBytes: Long,
    val createdAt: Instant
)
