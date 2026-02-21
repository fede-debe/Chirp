package com.project.chirp.api.dto

import com.project.chirp.domain.type.UserId

data class UserDto(
    val id: UserId,
    val email: String,
    val username: String,
    val hasEmailVerified: Boolean,
)
