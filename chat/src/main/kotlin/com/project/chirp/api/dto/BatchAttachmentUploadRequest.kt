package com.project.chirp.api.dto

import com.project.chirp.domain.type.ChatId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AttachmentMetadataInput(
    @field:NotBlank val mimeType: String,
    @field:NotBlank val originalFileName: String,
    val sizeInBytes: Long
)

/***
 * Request body for generating signed upload URLs for multiple message attachments in a single call.
 *
 * @param chatId: The ID of the chat the attachments will be sent in.
 * @param attachments: Metadata for each file to upload. Limited to 10 items per message.
 */
data class BatchAttachmentUploadRequest(
    val chatId: ChatId,
    @field:Size(max = 10, message = "A message cannot have more than 10 attachments")
    val attachments: List<AttachmentMetadataInput>
)
