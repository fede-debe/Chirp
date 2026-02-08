package com.project.chirp.infra.database.mappers

import com.project.chirp.domain.model.EmailVerificationToken
import com.project.chirp.infra.database.entities.EmailVerificationTokenEntity

fun EmailVerificationTokenEntity.toEmailVerificationToken(): EmailVerificationToken {
    return EmailVerificationToken(
        id = id,
        token = token,
        user = user.toUser()
    )
}