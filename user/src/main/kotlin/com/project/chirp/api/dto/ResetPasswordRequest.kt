package com.project.chirp.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.project.chirp.api.util.Password
import jakarta.validation.constraints.NotBlank

/***
 * Request to reset a user's password using a password reset token.
 * @param token: The password reset token.
 * @param newPassword: The new password to set.
 */
data class ResetPasswordRequest(
    @field:NotBlank
    @JsonProperty("token")
    val token: String,
    @field:Password
    @JsonProperty("newPassword")
    val newPassword: String
)