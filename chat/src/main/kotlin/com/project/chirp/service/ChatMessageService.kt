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
import com.project.chirp.infra.database.entities.ChatMessageAttachmentEntity
import com.project.chirp.infra.database.entities.ChatMessageEntity
import com.project.chirp.infra.database.mappers.toChatMessage
import com.project.chirp.infra.database.repositories.ChatMessageAttachmentRepository
import com.project.chirp.infra.database.repositories.ChatMessageRepository
import com.project.chirp.infra.database.repositories.ChatParticipantRepository
import com.project.chirp.infra.database.repositories.ChatRepository
import com.project.chirp.infra.message_queue.EventPublisher
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/***
 * Service for managing chat messages.
 * @param chatRepository: Repository for managing chat entities.
 * @param chatMessageRepository: Repository for managing chat message entities.
 * @param chatMessageAttachmentRepository: Repository for persisting file attachments linked to messages.
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
    private val chatMessageAttachmentRepository: ChatMessageAttachmentRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val eventPublisher: EventPublisher
) {
    /***
     * Sends a chat message, optionally with file attachments.
     *
     * @param chatId: The ID of the chat.
     * @param senderId: The ID of the sender of the message.
     * @param content: The content of the message.
     * @param messageId: The ID of the message (optional) set to null.
     *   Hibernate could generate the ID for us, but client sent messages by socket in real time and
     *   that will work in the form of a simple JSON object that after being validated by the server
     *   will broadcast this message via all active web socket connections to all participants of the
     *   chat. This means the sender of the message will receive the message back from the server if
     *   sent successfully. This ChatMessageId will be client side generated. It can be used by the
     *   client to compare the sent message with the received message and be sure delivery was successful.
     * @param attachmentUrls: Optional list of file attachment inputs to associate with the message.
     *   Each entry must reference a file that has already been uploaded to Supabase storage via
     *   the signed URL endpoint. A maximum of 10 attachments is enforced to prevent abuse.
     *   Attachments are saved after saveAndFlush so the parent message ID is available for the
     *   foreign key before the attachment INSERT is issued.
     * @return The sent chat message.
     * @throws ChatNotFoundException: If the chat is not found.
     * @throws ChatParticipantNotFoundException: If the sender is not found in the chat.
     * @throws IllegalArgumentException: If more than 10 attachments are supplied.
     *
     * @Transactional is needed here to execute the different queries in a single transaction so
     * that attachments and message are either both committed or both rolled back.
     * @CacheEvict evicts the cached list of messages for the given chat ID after saving a new message.
     * This ensures that the next request for chat messages will fetch the updated list from the database.
     */
    @Transactional
    @CacheEvict(
        value = ["messages"],
        key = "#chatId",
    )
    fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        content: String,
        messageId: ChatMessageId? = null,
        attachmentUrls: List<AttachmentInput>? = null
    ): ChatMessage {
        if (!attachmentUrls.isNullOrEmpty() && attachmentUrls.size > 10) {
            throw IllegalArgumentException("A message cannot have more than 10 attachments")
        }

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

        if (!attachmentUrls.isNullOrEmpty()) {
            val saved = chatMessageAttachmentRepository.saveAll(
                attachmentUrls.map { input ->
                    ChatMessageAttachmentEntity(
                        chatMessage = savedMessage,
                        storageUrl = input.storageUrl,
                        mimeType = input.mimeType,
                        originalFileName = input.originalFileName,
                        sizeInBytes = input.sizeInBytes,
                        durationInSeconds = input.durationInSeconds
                    )
                }
            )
            savedMessage.attachments.addAll(saved)
        }

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

        evictMessagesCache(message.chatId)
    }

    @CacheEvict(
        value = ["messages"],
        key = "#chatId",
    )
    fun evictMessagesCache(chatId: ChatId) {
        // NO-OP: Let Spring handle the cache evict
    }
}