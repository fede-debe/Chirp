package com.project.chirp.domain.model

import com.project.chirp.domain.type.UserId

data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val hasEmailVerified: Boolean,
    val typingIndicatorsEnabled: Boolean
)
