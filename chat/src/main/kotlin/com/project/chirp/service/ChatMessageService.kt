package com.project.chirp.service

import com.project.chirp.api.dto.ChatMessageDto
import com.project.chirp.api.mappers.toChatMessageDto
import com.project.chirp.domain.exception.ChatNotFoundException
import com.project.chirp.domain.exception.ChatParticipantNotFoundException
import com.project.chirp.domain.exception.ForbiddenException
import com.project.chirp.domain.exception.MessageNotFoundException
import com.project.chirp.domain.models.ChatMessage
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.ChatMessageEntity
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
 * Service for managing chat messages.
 * @param chatRepository: Repository for managing chat entities.
 * @param chatMessageRepository: Repository for managing chat message entities.
 * @param chatParticipantRepository: Repository for managing chat participant entities.
 *
 * @see getChatMessages Retrieves chat messages for a chat before a given time.
 * @see sendMessage Sends a chat message.
 * @see deleteMessage Deletes a chat message.
 */
@Service
class ChatMessageService(
    private val chatRepository: ChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatParticipantRepository: ChatParticipantRepository
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
     */
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

    /***
     * @param chatId: The ID of the chat.
     * @param senderId: The ID of the sender of the message.
     * @param content: The content of the message.
     * @param messageId: The ID of the message (optional) set to null.
     * Hibernate could generate the ID for us, but client sent message by socket in real time and that will work in
     * form of simple JSON object that after being validated by the server will broadcast this message via all active
     * web socket connections to all participants of the chat. This means the sender of the message will receive the
     * message back from the server if sent successfully. This ChatMessageId will be client side generated. It can be
     * used by the client to compare the sent message with the received message and be sure the delivery was successful.
     * @return The sent chat message.
     * @throws ChatNotFoundException: If the chat is not found.
     * @throws ChatParticipantNotFoundException: If the sender is not found in the chat.
     *
     * @Transactional is needed here to execute the different queries in a single transaction.
     */
    @Transactional
    fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        content: String,
        messageId: ChatMessageId? = null
    ): ChatMessage {
        val chat = chatRepository.findChatById(chatId, senderId)
            ?: throw ChatNotFoundException()
        val sender = chatParticipantRepository.findByIdOrNull(senderId)
            ?: throw ChatParticipantNotFoundException(senderId)

        val savedMessage = chatMessageRepository.save(
            ChatMessageEntity(
                id = messageId,
                content = content.trim(),
                chatId = chatId,
                chat = chat,
                sender = sender
            )
        )

        return savedMessage.toChatMessage()
    }

    /***
     * Deletes a chat message.
     * @param messageId: The ID of the message to delete.
     * @param requestUserId: The ID of the user requesting the deletion.
     * @throws MessageNotFoundException: If the message is not found.
     * @throws ForbiddenException: If the user is not the sender of the message.
     * */
    @Transactional
    fun deleteMessage(
        messageId: ChatMessageId,
        requestUserId: UserId
    ) {
        val message = chatMessageRepository.findByIdOrNull(messageId)
            ?: throw MessageNotFoundException(messageId)

        if (message.sender.userId != requestUserId) {
            throw ForbiddenException()
        }

        chatMessageRepository.delete(message)
    }
}