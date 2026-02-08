package com.project.chirp.domain.model

data class EmailVerificationToken(
    val id: Long,
    val token: String,
    val user: User
)