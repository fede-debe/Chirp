package com.project.chirp.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email

data class LoginRequest(
    @field:Email(message = "Must contain a valid email")
    @JsonProperty("email")
    val email: String,
    @JsonProperty("password")
    val password: String
)
