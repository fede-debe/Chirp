package com.project.chirp.domain.exception

import com.project.chirp.domain.type.UserId

class ChatParticipantNotFoundException(
    private val id: UserId
) : RuntimeException(
    "The chat participant with the ID $id was not found."
)