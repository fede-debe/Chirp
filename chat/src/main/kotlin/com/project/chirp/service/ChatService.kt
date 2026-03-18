package com.project.chirp.service

import com.project.chirp.api.dto.ChatMessageDto
import com.project.chirp.api.mappers.toChatMessageDto
import com.project.chirp.domain.event.*
import com.project.chirp.domain.exception.*
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
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/***
 * Service for managing chat-related operations.
 * @see getChatMessages Retrieves chat messages for a chat before a given time.
 * @see createChat Creates a chat between the creator and other participants.
 * @see addParticipantsToChat Adds participants to an existing chat.
 * @see removeParticipantFromChat Removes a participant from an existing chat.
 * @see getChatById Retrieves a chat by its ID.
 * @see findChatsByUser Retrieves all chats for a given user.
 */
@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    /***
     * Retrieves chat messages for a chat before a given time.
     * @param chatId: The unique identifier for the chat.
     * @param before: The timestamp before which chat messages should be fetched.
     * @param pageSize: The number of chat messages to fetch.
     * @return A list of chat messages for the given chat before the given time.
     *
     * We will later cache these messages for a given chat in Redis, and we need to do that
     * with the ChatMessageDto type because this is what all the client needs. The ChatMessageDto
     * don't contain the entire sender information, only the sender ID which is much lighter
     *
     * @Cacheable caches the result of this method in Redis for a given chat ID because we need to
     * know which specific cached list of messages we want to load.
     * The condition ensures that we cache only the first page of chat messages.
     * The sync parameter ensures that if 2 simultaneous requests are coming, it will wait for the
     * first one to make the DB query to populate and cache and the second then immediately respond
     * with the cached data. The dto contains exactly the data we want to respond with and cached.
     *
     * If someone update the cached list of messages, the next request will get the updated list.
     */
    @Cacheable(
        value = ["messages"],
        key = "#chatId",
        condition = "#before == null && #pageSize <= 50",
        sync = true
    )
    fun getChatMessages(
        chatId: ChatId,
        before: Instant?,
        pageSize: Int
    ): List<ChatMessageDto> {
        return chatMessageRepository
            .findByChatIdBefore(
                chatId = chatId,
                before = before ?: Instant.now(),
                pageable = PageRequest.of(0, pageSize) // page number is zero since we use timestamp as a page parameter
            )
            .content // need to pass a list from the returned Slice instance
            .asReversed() // the query loads the 20 most recent messages, and we need the latest message at the bottom of the list
            .map { it.toChatMessage().toChatMessageDto() }
    }

    /**
     * Retrieves a chat by its ID.
     * @param chatId: The unique identifier for the chat.
     * @param requestUserId: The ID of the user requesting the chat, we need to check if user is part of the chat.
     * @return The chat if found, otherwise null.
     */
    fun getChatById(
        chatId: ChatId,
        requestUserId: UserId
    ): Chat? {
        return chatRepository
            .findChatById(chatId, requestUserId)
            ?.toChat(lastMessageForChat(chatId))
    }

    /**
     * Retrieves all chats for a given user.
     * @param userId: The ID of the user.
     * @return A list of chats for the given user.
     */
    fun findChatsByUser(userId: UserId): List<Chat> {
        val chatEntities = chatRepository.findAllByUserId(userId)
        val chatIds = chatEntities.mapNotNull { it.id }
        val latestMessages = chatMessageRepository
            .findLatestMessagesByChatIds(chatIds.toSet())
            .associateBy { it.chatId } // take latest message for each chat

        return chatEntities
            .map {
                it.toChat(lastMessage = latestMessages[it.id]?.toChatMessage())
            }
            .sortedByDescending { it.lastActivityAt }
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
        ).toChat(lastMessage = null).also { entity ->
            applicationEventPublisher.publishEvent(
                ChatCreatedEvent(
                    chatId = entity.id,
                    participantIds = entity.participants.map { it.userId }
                )
            )
        }
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

        applicationEventPublisher.publishEvent(
            ChatParticipantsJoinedEvent(
                chatId = chatId,
                userIds = userIds
            )
        )

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

        if (userId == chat.creator.userId) {
            val allParticipantIds = chat.participants.map { it.userId }.toSet()
            chatRepository.delete(chat)
            applicationEventPublisher.publishEvent(
                ChatDeletedEvent(chatId = chatId, participantIds = allParticipantIds)
            )
            return
        }

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

        applicationEventPublisher.publishEvent(
            ChatParticipantLeftEvent(
                chatId = chatId,
                userId = userId
            )
        )
    }

    @Transactional
    fun removeParticipantAsAdmin(chatId: ChatId, adminId: UserId, targetUserId: UserId) {
        val chat = chatRepository.findByIdOrNull(chatId)
            ?: throw ChatNotFoundException()

        if (chat.creator.userId != adminId) {
            throw NotChatAdminException()
        }

        if (adminId == targetUserId) {
            throw ForbiddenException()
        }

        val target = chat.participants.find { it.userId == targetUserId }
            ?: throw ChatParticipantNotFoundException(targetUserId)

        chatRepository.save(
            chat.apply {
                this.participants = chat.participants - target
            }
        )

        val remainingParticipantIds = chat.participants
            .map { it.userId }
            .filter { it != targetUserId }
            .toSet()

        applicationEventPublisher.publishEvent(
            ParticipantRemovedByAdminEvent(
                chatId = chatId,
                removedUserId = targetUserId,
                remainingParticipantIds = remainingParticipantIds
            )
        )
    }

    fun getParticipantUsername(userId: UserId): String? {
        return chatParticipantRepository.findByIdOrNull(userId)?.username
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