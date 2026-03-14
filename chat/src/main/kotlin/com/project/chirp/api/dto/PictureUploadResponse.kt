package com.project.chirp.api.dto

import com.project.chirp.domain.models.ProfilePictureUploadCredentials
import java.time.Instant

/***
 * @see ProfilePictureUploadCredentials DTO variant.
 * @param uploadUrl The URL for uploading the file.
 * @param publicUrl The public URL of the uploaded file.
 * @param headers The headers required for the upload request.
 * @param expiresAt The expiration time for the upload URL.
 */
data class PictureUploadResponse(
    val uploadUrl: String,
    val publicUrl: String,
    val headers: Map<String, String>,
    val expiresAt: Instant
)