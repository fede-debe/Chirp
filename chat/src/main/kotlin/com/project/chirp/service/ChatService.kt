package com.project.chirp.service

import com.project.chirp.api.dto.ChatMessageDto
import com.project.chirp.api.mappers.toChatMessageDto
import com.project.chirp.domain.exception.ChatNotFoundException
import com.project.chirp.domain.exception.ChatParticipantNotFoundException
import com.project.chirp.domain.exception.ForbiddenException
import com.project.chirp.domain.exception.InvalidChatSizeException
import com.project.chirp.domain.models.Chat
import com.project.chirp.domain.models.ChatMessage
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.ChatEntity
import com.project.chirp.infra.database.mappers.toChat
import com.project.chirp.infra.database.mappers.toChatMessage
import com.project.chirp.infra.database.repositories.ChatMessageRepository
import com.project.chirp.infra.database.repositories.ChatParticipantRepository
import com.project.chirp.infra.database.repositories.ChatRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/***
 * Service for managing chat-related operations.
 * @see createChat Creates a chat between the creator and other participants.
 * @see addParticipantsToChat Adds participants to an existing chat.
 * @see removeParticipantFromChat Removes a participant from an existing chat.
 */
@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {

    fun getChatMessages(
        chatId: ChatId,
        before: Instant?,
        pageSize: Int
    ): List<ChatMessageDto> {
        return chatMessageRepository
            .findByChatIdBefore(
                chatId = chatId,
                before = before ?: Instant.now(),
                pageable = PageRequest.of(0, pageSize)
            )
            .content
            .asReversed()
            .map { it.toChatMessage().toChatMessageDto() }
    }

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

    @Transactional
    fun addParticipantsToChat(
        requestUserId: UserId,
        chatId: ChatId,
        userIds: Set<UserId>
    ): Chat {
        val chat = chatRepository.findByIdOrNull(chatId)
            ?: throw ChatNotFoundException()

        val isRequestingUserInChat = chat.participants.any {
            it.userId == requestUserId
        }
        if (!isRequestingUserInChat) {
            throw ForbiddenException()
        }

        val users = userIds.map { userId ->
            chatParticipantRepository.findByIdOrNull(userId)
                ?: throw ChatParticipantNotFoundException(userId)
        }

        val lastMessage = lastMessageForChat(chatId)
        val updatedChat = chatRepository.save(
            chat.apply {
                this.participants = chat.participants + users
            }
        ).toChat(lastMessage)

        return updatedChat
    }

    @Transactional
    fun removeParticipantFromChat(
        chatId: ChatId,
        userId: UserId
    ) {
        val chat = chatRepository.findByIdOrNull(chatId)
            ?: throw ChatNotFoundException()
        val participant = chat.participants.find { it.userId == userId }
            ?: throw ChatParticipantNotFoundException(userId)

        /***
         * If removing the participant will leave the chat with only one participant, delete the chat.
         * If a chat is deleted, all its messages will also be deleted (everything related to the chat will be deleted).
         * This will be done by annotation @OnDelete(action = OnDeleteAction.CASCADE) on the chat property of ChatMessageEntity.
         * The annotation will not work if we don't set the action in Supabase as well (Action if reference row is removed).
         */
        val newParticipantsSize = chat.participants.size - 1
        if (newParticipantsSize == 0) {
            chatRepository.deleteById(chatId)
            return
        }

        chatRepository.save(
            chat.apply {
                this.participants = chat.participants - participant
            }
        )
    }

    /***
     * @param chatId: The ID of the chat.
     * @return The latest chat message for the chat.
     */
    private fun lastMessageForChat(chatId: ChatId): ChatMessage? {
        return chatMessageRepository
            .findLatestMessagesByChatIds(setOf(chatId))
            .firstOrNull()
            ?.toChatMessage()
    }
}