package com.project.chirp.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.project.chirp.api.util.Password
import jakarta.validation.constraints.NotBlank

/***
 * Request to change a user's password.
 * @param oldPassword: The user's current password.
 * @param newPassword: The new password to set.
 */
data class ChangePasswordRequest(
    @field:NotBlank
    @JsonProperty("oldPassword")
    val oldPassword: String,
    @field:Password
    @JsonProperty("newPassword")
    val newPassword: String
)