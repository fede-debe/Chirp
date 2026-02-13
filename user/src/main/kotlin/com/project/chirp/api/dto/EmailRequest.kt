package com.project.chirp.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email

/***
 * Request to send a password reset email.
 * @param email: The user's email address.
 */
data class EmailRequest(
    @field:Email(message = "Please provide a valid email address")
    @JsonProperty("email")
    val email: String
)