package com.project.chirp.service

import com.project.chirp.domain.exception.InvalidDeviceTokenException
import com.project.chirp.domain.model.DeviceToken
import com.project.chirp.domain.model.DeviceToken.Platform
import com.project.chirp.domain.model.PushNotification
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.DeviceTokenEntity
import com.project.chirp.infra.database.DeviceTokenRepository
import com.project.chirp.infra.mappers.toDeviceToken
import com.project.chirp.infra.mappers.toPlatformEntity
import com.project.chirp.infra.push_notification.FirebasePushNotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/***
 * Service for managing push notifications.
 * @see registerDevice Registers a device token for a user.
 * @see unregisterDevice Unregisters a device token.
 * @see sendNewMessageNotifications Sends a new message notification to a list of recipients.
 *
 * @param deviceTokenRepository: Repository for managing device token entities.
 * @param firebasePushNotificationService: Service for sending push notifications via Firebase.
 *
 * @author fede-debe
 * @since 1.0.0
 */
@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val firebasePushNotificationService: FirebasePushNotificationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registerDevice(
        userId: UserId,
        token: String,
        platform: Platform
    ): DeviceToken {
        val existing = deviceTokenRepository.findByToken(token)

        val trimmedToken = token.trim()
        // if not valid, throw exception
        if (existing == null && !firebasePushNotificationService.isValidToken(trimmedToken)) {
            throw InvalidDeviceTokenException()
        }

        // if exists, update user id
        val entity = if (existing != null) {
            deviceTokenRepository.save(
                existing.apply {
                    this.userId = userId
                }
            )
        } else {
            deviceTokenRepository.save(
                DeviceTokenEntity(
                    userId = userId,
                    token = trimmedToken,
                    platform = platform.toPlatformEntity()
                )
            )
        }

        return entity.toDeviceToken()
    }

    @Transactional
    fun unregisterDevice(token: String) {
        deviceTokenRepository.deleteByToken(token.trim())
    }

    /***
     * Sends a new message notification to a list of recipients.
     *
     * @param recipientUserIds: The IDs of the recipients of the message.
     * @param senderUserId: The ID of the sender.
     * @param senderUsername: The username of the sender.
     * @param message: The content of the message.
     * @param chatId: The ID of the chat where the message was sent.
     */
    fun sendNewMessageNotifications(
        recipientUserIds: List<UserId>,
        senderUserId: UserId,
        senderUsername: String,
        message: String,
        chatId: ChatId
    ) {
        val deviceTokens = deviceTokenRepository.findByUserIdIn(recipientUserIds)
        if (deviceTokens.isEmpty()) {
            logger.info("No device tokens found for $recipientUserIds")
            return
        }

        val recipients = deviceTokens
            .filter { it.userId != senderUserId }
            .map { it.toDeviceToken() }

        val notification = PushNotification(
            title = "New message from $senderUsername",
            recipients = recipients,
            message = message,
            chatId = chatId,
            data = mapOf(
                "chatId" to chatId.toString(),
                "type" to "new_message"
            )
        )

        firebasePushNotificationService.sendNotification(notification)
    }
}