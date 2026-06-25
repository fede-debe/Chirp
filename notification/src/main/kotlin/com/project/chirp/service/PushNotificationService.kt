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
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap

/***
 * Service for managing push notifications.
 * @see registerDevice Registers a device token for a user.
 * @see unregisterDevice Unregisters a device token.
 * @see sendNewMessageNotifications Sends a new message notification to a list of recipients.
 * @see processRetries Processes failed push notifications and retries them.
 * @see sendWithRetry Sends a push notification with retry logic.
 * @see scheduleRetry Schedules a retry for a failed push notification.
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
    /***
     * Configuration for retrying failed push notifications.
     * If notification fails, it will be retried up to 5 times with increasing delays.
     */
    companion object {
        private val RETRY_DELAYS_SECONDS = listOf(
            30L, // First retry after 30 seconds
            60L, // Second retry after 1 minute
            120L, // Third retry after 2 minutes
            300L, // Fourth retry after 5 minutes
            600L // Fifth retry after 10 minutes
        )
        const val MAX_RETRY_AGE_MINUTES = 30L // drop the entire queue after 30 minutes
    }

    // track failed notifications with corresponding timestamps
    // ConcurrentSkipListMap is a sorted map that allows efficient retrieval of entries within a specified time range.
    private val retryQueue = ConcurrentSkipListMap<Long, MutableList<RetryData>>()

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

        /** After excluding the sender's own device(s) there may be nobody left to notify (e.g. the
         *  sender is the only participant with a registered token). Firebase rejects an empty send,
         *  so return early instead of building an empty notification. */
        if (recipients.isEmpty()) {
            logger.info("No recipients to notify for chat $chatId after excluding the sender")
            return
        }

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

        sendWithRetry(notification = notification)
    }

    /***
     * Sends a push notification with retry logic.
     * @param notification: The push notification to send.
     * @param attempt: The current retry attempt for this notification.
     */
    fun sendWithRetry(
        notification: PushNotification,
        attempt: Int = 0
    ) {
        // try to send notification
        val result = firebasePushNotificationService.sendNotification(notification)

        // delete device tokens that failed permanently
        result.permanentFailures.forEach {
            deviceTokenRepository.deleteByToken(it.token)
        }

        // if there are temporary failures and we have retries left, schedule a retry
        if (result.temporaryFailures.isNotEmpty() && attempt < RETRY_DELAYS_SECONDS.size) {
            val retryNotification = notification.copy(
                recipients = result.temporaryFailures
            )
            scheduleRetry(retryNotification, attempt + 1)
        }

        // if there are successful recipients, log success
        if (result.succeeded.isNotEmpty()) {
            logger.info("Successfully sent notification to ${result.succeeded.size} devices")
        }
    }

    /***
     * Schedules a retry for a failed push notification.
     * @param notification: The push notification to retry.
     * @param attempt: The current retry attempt for this notification.
     */
    private fun scheduleRetry(
        notification: PushNotification,
        attempt: Int
    ) {
        // calculate delay for retry and check if we have retries left
        val delay = RETRY_DELAYS_SECONDS.getOrElse(attempt - 1) {
            RETRY_DELAYS_SECONDS.last()
        }
        val executeAt = Instant.now().plusSeconds(delay)
        val executeAtMillis = executeAt.toEpochMilli()

        val retryData = RetryData(
            notification = notification,
            attempt = attempt,
            createdAt = Instant.now()
        )

        // we only care about retries that are not too old
        retryQueue.compute(executeAtMillis) { _, retries ->
            (retries ?: mutableListOf()).apply { add(retryData) }
        }

        logger.info("Scheduled retry $attempt for ${notification.id} in $delay seconds")
    }

    /***
     * Processes failed push notifications and retries them.
     * Every 15 seconds, it checks for possible retries scheduled within the last 30 minutes.
     * Notifications are retried up to 5 times with increasing delays.
     * Notifications older than 30 minutes are dropped.
     */
    @Scheduled(fixedDelay = 15_000L)
    fun processRetries() {
        val now = Instant.now()
        val nowMillis = now.toEpochMilli()

        val toProcess = retryQueue.headMap(nowMillis, true)

        if (toProcess.isEmpty()) {
            return
        }

        val entries = toProcess.entries.toList()
        entries.forEach { (timeMillis, retries) ->
            retryQueue.remove(timeMillis)

            retries.forEach { retry ->
                try {
                    val age = Duration.between(retry.createdAt, now)
                    if (age.toMinutes() > MAX_RETRY_AGE_MINUTES) {
                        logger.warn("Dropping old retry (${age.toMinutes()} old)")
                        return@forEach
                    }

                    sendWithRetry(
                        notification = retry.notification,
                        attempt = retry.attempt
                    )
                } catch (e: Exception) {
                    logger.warn("Error processing retry ${retry.notification.id}", e)
                }
            }
        }
    }

    /***
     * Data class for tracking retries of failed push notifications.
     * @param notification: The push notification that failed.
     * @param attempt: The current retry attempt.
     * @param createdAt: The timestamp when the retry was scheduled.
     */
    private data class RetryData(
        val notification: PushNotification,
        val attempt: Int,
        val createdAt: Instant
    )
}