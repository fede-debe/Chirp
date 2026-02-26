package com.project.chirp.service

import com.project.chirp.domain.exception.ChatParticipantNotFoundException
import com.project.chirp.domain.exception.InvalidChatSizeException
import com.project.chirp.domain.models.Chat
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.ChatEntity
import com.project.chirp.infra.database.mappers.toChat
import com.project.chirp.infra.database.repositories.ChatParticipantRepository
import com.project.chirp.infra.database.repositories.ChatRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/***
 * Service for managing chat-related operations.
 * @see createChat Creates a chat between the creator and other participants.
 *
 */
@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
) {
    /***
     * @param creatorId: The ID of the creator of the chat.
     * @param otherUserIds: The IDs of the other participants in the chat.
     * @return The created chat.
     * @throws InvalidChatSizeException: If there are less than 2 unique participants.
     * @throws ChatParticipantNotFoundException: If the creator or any other participant is not found.
     */
    @Transactional
    fun createChat(
        creatorId: UserId,
        otherUserIds: Set<UserId>
    ): Chat {
        val otherParticipants = chatParticipantRepository.findByUserIdIn(
            userIds = otherUserIds
        )

        val allParticipants = (otherParticipants + creatorId)
        if (allParticipants.size < 2) {
            throw InvalidChatSizeException()
        }

        val creator = chatParticipantRepository.findByIdOrNull(creatorId)
            ?: throw ChatParticipantNotFoundException(creatorId)

        return chatRepository.save(
            ChatEntity(
                creator = creator,
                participants = setOf(creator) + otherParticipants
            )
        ).toChat(lastMessage = null)
    }
}