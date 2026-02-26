package com.project.chirp.infra.messaging

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.domain.models.ChatParticipant
import com.project.chirp.infra.message_queue.MessageQueues
import com.project.chirp.service.ChatParticipantService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/***
 * Handles chat-related user events.
 *
 * This class listens for user events related to chat and creates chat participants accordingly.
 * It is responsible for creating chat participants when a user is verified.
 * @see handleUserEvent: Handles user events related to chat.
 */
@Component
class ChatUserEventListener(
    private val chatParticipantService: ChatParticipantService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.CHAT_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        logger.info("Received user event: {}", event)
        when (event) {
            is UserEvent.Verified -> {
                chatParticipantService.createChatParticipant(
                    chatParticipant = ChatParticipant(
                        userId = event.userId,
                        username = event.username,
                        email = event.email,
                        profilePictureUrl = null
                    )
                )
            }

            else -> Unit
        }
    }
}