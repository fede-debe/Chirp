package com.project.chirp.domain.exception

import com.project.chirp.domain.type.ChatMessageId

class MessageNotFoundException(
    private val id: ChatMessageId
) : RuntimeException(
    "Message with ID $id not found"
)