package com.project.chirp.api.dto

import jakarta.validation.constraints.NotBlank

/***
 * Request to confirm a profile picture upload to supabase.
 * @param publicUrl The public URL of the uploaded profile picture.
 */
data class ConfirmProfilePictureRequest(
    @field:NotBlank
    val publicUrl: String
)