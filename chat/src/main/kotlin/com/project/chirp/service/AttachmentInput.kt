package com.project.chirp.service

/***
 * Represents the data the client supplies when attaching a file to a chat message.
 *
 * The client is responsible for uploading the file to Supabase storage first (via the
 * /api/messages/attachments/upload-url endpoint) and then passing the resulting public URL here.
 * The backend stores these values as-is on ChatMessageAttachmentEntity without re-validating
 * the file, because the signed upload URL already enforces the allowed MIME type at upload time.
 *
 * @param storageUrl: The publicly accessible URL of the already-uploaded file.
 * @param mimeType: The MIME type of the file as declared by the client.
 * @param originalFileName: The original name of the file on the client device.
 * @param sizeInBytes: Size of the file in bytes as reported by the client.
 */
data class AttachmentInput(
    val storageUrl: String,
    val mimeType: String,
    val originalFileName: String,
    val sizeInBytes: Long,
    val durationInSeconds: Long? = null
)