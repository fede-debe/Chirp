package com.project.chirp.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.project.chirp.api.util.Password
import jakarta.validation.constraints.Email
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

    @field:Password
    @JsonProperty("password")
    val password: String
)
