package com.project.chirp.infra.storage

import com.project.chirp.api.dto.AttachmentMetadataInput
import com.project.chirp.domain.exception.InvalidProfilePictureException
import com.project.chirp.domain.exception.StorageException
import com.project.chirp.domain.models.ProfilePictureUploadCredentials
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.*

/***
 * Handles file storage operations using Supabase. Provides functionalities to other classes.
 * Rest client for Supabase storage API, makes normal HTTP calls to request the presign URL
 * from Supabase for file uploads.
 *
 * Normally backend receives API calls, but here we need to make HTTP requests to Supabase.
 *
 * @see generateSignedUploadUrl: Generates a signed URL for uploading a profile picture to Supabase.
 * @see generateMessageAttachmentUploadUrl: Generates a signed URL for uploading a message attachment to Supabase.
 * @see deleteFile: Deletes a file from Supabase.
 * @see createSignedUrl: Creates a signed URL for uploading a file to Supabase.
 */
@Service
class SupabaseStorageService(
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    private val supabaseRestClient: RestClient,
) {
    companion object {
        private val allowedMimeTypes = mapOf(
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }

    fun generateSignedUploadUrl(userId: UserId, mimeType: String): ProfilePictureUploadCredentials {
        val extension = allowedMimeTypes[mimeType]
            ?: throw InvalidProfilePictureException("Invalid mime type $mimeType")

        val fileName = "user_${userId}_${UUID.randomUUID()}.$extension"
        // bucketName/$fileName
        val path = "profile-pictures/$fileName"

        // public bucket
        val publicUrl = "$supabaseUrl/storage/v1/object/public/$path"

        return ProfilePictureUploadCredentials(
            uploadUrl = createSignedUrl(
                path = path,
                expiresInSeconds = 300
            ),
            publicUrl = publicUrl,
            headers = mapOf(
                "Content-Type" to mimeType
            ),
            expiresAt = Instant.now().plusSeconds(300)
        )
    }

    /***
     * Generates a signed Supabase upload URL for a message attachment.
     *
     * Files are stored under message-attachments/{chatId}/{userId}/{uuid}.{extension} so that
     * attachments are naturally grouped by chat and sender, making future bulk-delete operations
     * straightforward (e.g. when a chat is deleted from storage).
     *
     * The signed URL expires in 5 minutes — the same window used for profile pictures — which is
     * sufficient for a direct browser-to-Supabase upload without exposing a long-lived credential.
     *
     * @param userId: The ID of the user uploading the attachment.
     * @param chatId: The ID of the chat the message belongs to. Used to scope the storage path.
     * @param mimeType: The MIME type of the file. Must be one of the allowed types (image/jpeg,
     *   image/jpg, image/png, image/webp) — the same set enforced for profile pictures.
     * @return ProfilePictureUploadCredentials containing the signed upload URL, the future public
     *   URL, required headers, and the credential expiry time. ProfilePictureUploadCredentials is
     *   reused here because the structure is identical; no separate type is needed.
     * @throws InvalidProfilePictureException: If the supplied mimeType is not in the allowed set.
     */
    fun generateMessageAttachmentUploadUrl(
        userId: UserId,
        chatId: ChatId,
        mimeType: String
    ): ProfilePictureUploadCredentials {
        val extension = allowedMimeTypes[mimeType]
            ?: throw InvalidProfilePictureException("Invalid mime type $mimeType")

        val path = "message-attachments/$chatId/$userId/${UUID.randomUUID()}.$extension"
        val publicUrl = "$supabaseUrl/storage/v1/object/public/$path"

        return ProfilePictureUploadCredentials(
            uploadUrl = createSignedUrl(
                path = path,
                expiresInSeconds = 300
            ),
            publicUrl = publicUrl,
            headers = mapOf(
                "Content-Type" to mimeType
            ),
            expiresAt = Instant.now().plusSeconds(300)
        )
    }

    fun generateMessageAttachmentUploadUrls(
        userId: UserId,
        chatId: ChatId,
        attachments: List<AttachmentMetadataInput>
    ): List<ProfilePictureUploadCredentials> =
        attachments.map { generateMessageAttachmentUploadUrl(userId, chatId, it.mimeType) }

    fun deleteFile(url: String) {
        val path = if (url.contains("/object/public/")) {
            url.substringAfter("/object/public/")
        } else throw StorageException("Invalid file URL format")

        val deleteUrl = "/storage/v1/object/$path"

        val response = supabaseRestClient
            .delete()
            .uri(deleteUrl)
            .retrieve()
            .toBodilessEntity()

        if (response.statusCode.isError) {
            throw StorageException("Unable to delete file: ${response.statusCode.value()}")
        }
    }

    private fun createSignedUrl(
        path: String,
        expiresInSeconds: Int
    ): String {
        val json = """
            { "expiresIn": $expiresInSeconds }
        """.trimIndent()

        val response = supabaseRestClient
            .post()
            .uri("/storage/v1/object/upload/sign/$path")
            .header("Content-Type", "application/json")
            .body(json)
            .retrieve()
            .body(SignedUploadResponse::class.java)
            ?: throw StorageException("Failed to create signed URL")

        return "$supabaseUrl/storage/v1${response.url}"
    }

    /**
     * Represents the response from Supabase for creating a signed URL for uploading a file.
     * @param url: The signed URL for uploading the file.
     */
    private data class SignedUploadResponse(
        val url: String
    )
}