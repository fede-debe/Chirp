package com.project.chirp.infra.message_queue

import com.project.chirp.domain.events.chat.ChatEvent
import com.project.chirp.service.PushNotificationService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Handles chat-related events for notifications.
 *
 * @param pushNotificationService: Service for sending push notifications.
 *
 * This class listens for chat-related events and sends push notifications accordingly.
 * @see handleUserEvent: Handles chat-related events.
 */
@Component
class NotificationChatEventListener(
    private val pushNotificationService: PushNotificationService
) {

    @RabbitListener(queues = [MessageQueues.NOTIFICATION_CHAT_EVENTS])
    fun handleUserEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.NewMessage -> {
                pushNotificationService.sendNewMessageNotifications(
                    recipientUserIds = event.recipientIds.toList(),
                    senderUserId = event.senderId,
                    senderUsername = event.senderUsername,
                    message = event.message,
                    chatId = event.chatId
                )
            }
        }
    }
}