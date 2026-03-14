package com.project.chirp.api.mappers

import com.project.chirp.api.dto.PictureUploadResponse
import com.project.chirp.domain.models.ProfilePictureUploadCredentials

/***
 * Converts a ProfilePictureUploadCredentials object to a PictureUploadResponse dto object.
 */
fun ProfilePictureUploadCredentials.toResponse(): PictureUploadResponse {
    return PictureUploadResponse(
        uploadUrl = uploadUrl,
        publicUrl = publicUrl,
        headers = headers,
        expiresAt = expiresAt
    )
}