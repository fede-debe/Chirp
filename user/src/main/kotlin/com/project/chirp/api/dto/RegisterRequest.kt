package com.project.chirp.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

/** Request client make to the server
 * @param password using spring validation to provide rules */
data class RegisterRequest(

    @field:Length(min = 3, max = 20, message = "Username must be between 3 and 20 characters long")
    @JsonProperty("username")
    val username: String,

    @field:Email(message = "Please provide a valid email address")
    @JsonProperty("email")
    val email: String,

    @field:Pattern(
        regexp = "^(?=.*[\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])(.{8,})$",
        message = "Password must be at least 8 characters and contain at least one digit or special character"
    )
    @JsonProperty("password")
    val password: String
)
