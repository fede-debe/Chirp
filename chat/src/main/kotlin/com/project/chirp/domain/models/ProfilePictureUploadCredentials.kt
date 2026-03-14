package com.project.chirp.domain.models

import java.time.Instant

/***
 * Represents credentials for uploading a profile picture.
 * @param uploadUrl: The URL for uploading the profile picture.
 * @param publicUrl: The public URL for accessing the uploaded profile picture.
 * @param headers: Headers required for uploading the profile picture.
 * @param expiresAt: The time when the credentials expire.
 */
data class ProfilePictureUploadCredentials(
    val uploadUrl: String,
    val publicUrl: String,
    val headers: Map<String, String>,
    val expiresAt: Instant
)