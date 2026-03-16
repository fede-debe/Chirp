package com.project.chirp.domain.event

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId

data class ParticipantRemovedByAdminEvent(
    val chatId: ChatId,
    val removedUserId: UserId,
    val remainingParticipantIds: Set<UserId>
)
