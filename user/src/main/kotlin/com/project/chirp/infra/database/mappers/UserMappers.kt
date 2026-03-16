package com.project.chirp.infra.database.mappers

import com.project.chirp.domain.model.User
import com.project.chirp.infra.database.entities.UserEntity

fun UserEntity.toUser(): User {
    return User(
        id = id!!,
        email = email,
        username = username,
        hasEmailVerified = hasVerifiedEmail,
        typingIndicatorsEnabled = typingIndicatorsEnabled,
    )
}