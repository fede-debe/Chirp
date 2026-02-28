package com.project.chirp.service

import com.project.chirp.domain.event.MessageDeletedEvent
import com.project.chirp.domain.events.chat.ChatEvent
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
import com.project.chirp.infra.message_queue.EventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/***
 * Service for managing chat messages.
 * @param chatRepository: Repository for managing chat entities.
 * @param chatMessageRepository: Repository for managing chat message entities.
 * @param chatParticipantRepository: Repository for managing chat participant entities.
 * @param applicationEventPublisher: Publisher for application events.
 * @param eventPublisher: Publisher for RabbitMQ events.
 *
 * @see sendMessage Sends a chat message.
 * @see deleteMessage Deletes a chat message.
 */
@Service
class ChatMessageService(
    private val chatRepository: ChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val eventPublisher: EventPublisher
) {
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

        /***
         * We need to flush the changes to the database immediately after saving the message.
         * This is because we need to get the ID of the message to send it to the clients.
         * Without flushing, we wouldn't have updated information of inserted save message.
         */
        val savedMessage = chatMessageRepository.saveAndFlush(
            ChatMessageEntity(
                id = messageId,
                content = content.trim(),
                chatId = chatId,
                chat = chat,
                sender = sender
            )
        )

        eventPublisher.publish(
            event = ChatEvent.NewMessage(
                senderId = sender.userId,
                senderUsername = sender.username,
                recipientIds = chat.participants.map { it.userId }.toSet(),
                chatId = chatId,
                message = savedMessage.content
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

        applicationEventPublisher.publishEvent(
            MessageDeletedEvent(
                chatId = message.chatId,
                messageId = messageId
            )
        )
    }
}