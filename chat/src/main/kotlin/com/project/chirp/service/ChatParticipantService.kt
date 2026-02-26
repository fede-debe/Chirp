package com.project.chirp.service

import com.project.chirp.domain.models.ChatParticipant
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.mappers.toChatParticipant
import com.project.chirp.infra.database.mappers.toChatParticipantEntity
import com.project.chirp.infra.database.repositories.ChatParticipantRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/***
 * Service for managing chat participants.
 * @see createChatParticipant Creates a chat participant.
 * @see findChatParticipantById Finds a chat participant by their user ID.
 * @see findChatParticipantByEmailOrUsername Finds a chat participant by their email or username.
 *
 */
@Service
class ChatParticipantService(
    private val chatParticipantRepository: ChatParticipantRepository,
) {

    fun createChatParticipant(
        chatParticipant: ChatParticipant
    ) {
        if (chatParticipantRepository.existsById(chatParticipant.userId)) return
        val normalizedEmail = chatParticipant.email.lowercase().trim()
        if (chatParticipantRepository.findByEmailOrUsername(normalizedEmail) != null) return
        chatParticipantRepository.save(
            chatParticipant.toChatParticipantEntity()
        )
    }

    fun findChatParticipantById(userId: UserId): ChatParticipant? {
        return chatParticipantRepository.findByIdOrNull(userId)?.toChatParticipant()
    }

    fun findChatParticipantByEmailOrUsername(
        query: String
    ): ChatParticipant? {
        val normalizedQuery = query.lowercase().trim()
        return chatParticipantRepository.findByEmailOrUsername(
            query = normalizedQuery
        )?.toChatParticipant()
    }
}