package com.project.chirp.infra.push_notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.*
import com.project.chirp.domain.model.DeviceToken
import com.project.chirp.domain.model.PushNotification
import com.project.chirp.domain.model.PushNotificationSendResult
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

/***
 * Service for sending push notifications using Firebase.
 *
 * @param credentialsPath: Path to the Firebase credentials file.
 * @param resourceLoader: Spring resource loader which takes in the credentialsPath
 * in order to load the underlying JSON.
 *
 * We validate a device token because with push notifications, there are 2 main ways to send notifications:
 * 1) Topics: This is a way to send notifications to a group of devices that have subscribed to a specific topic (e.g. "news").
 * 2) Device Tokens: This is a way to send notifications to a specific device (e.g. a user's phone). One-to-one communication.
 */
@Service
class FirebasePushNotificationService(
    @param:Value("\${firebase.credentials-path}")
    private val credentialsPath: String,
    private val resourceLoader: ResourceLoader
) {

    private val logger = LoggerFactory.getLogger(FirebasePushNotificationService::class.java)

    /***
     * Initializes the Firebase Admin SDK.
     * @PostConstruct annotation ensures that this method is called after the real spring
     * boot backend has been properly initialized.
     * */
    @PostConstruct
    fun initialize() {
        try {
            val serviceAccount = resourceLoader.getResource(credentialsPath)

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount.inputStream))
                .build()

            FirebaseApp.initializeApp(options)
            logger.info("Firebase Admin SDK initialized successfully")
        } catch (e: Exception) {
            logger.error("Error initializing Firebase Admin SDK", e)
            throw e
        }
    }

    /***
     * Validates a Firebase token.
     * @param token: The Firebase token to validate.
     * @return True if the token is valid, false otherwise.
     * */
    fun isValidToken(token: String): Boolean {
        val message = Message.builder()
            .setToken(token)
            .build()

        return try {
            FirebaseMessaging.getInstance().send(message, true)
            true
        } catch (e: FirebaseMessagingException) {
            logger.warn("Failed to validate Firebase token", e)
            false
        }
    }

    /***
     * Sends a push notification to one or more devices.
     * @param notification: The notification to send.
     * @return The result of the notification send operation.
     *
     * Conditionally check the platform to either use AndroidConfig or ApnsConfig.
     * setCollapseKey/setThreadId is useful with chats to collapse multiple notifications into one.
     * If multiple notifications are received for the same chat will be collapsed into one.
     * */
    fun sendNotification(notification: PushNotification): PushNotificationSendResult {
        val messages = notification.recipients.map { recipient ->
            Message.builder()
                .setToken(recipient.token)
                .setNotification(
                    Notification.builder()
                        .setTitle(notification.title)
                        .setBody(notification.message)
                        .build()
                )
                .apply {
                    notification.data.forEach { (key, value) ->
                        putData(key, value)
                    }

                    when (recipient.platform) {
                        DeviceToken.Platform.ANDROID -> {
                            setAndroidConfig(
                                AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .setCollapseKey(notification.chatId.toString())
                                    .setRestrictedPackageName("com.project.chirp")
                                    .build()
                            )
                        }

                        DeviceToken.Platform.IOS -> {
                            setApnsConfig(
                                ApnsConfig.builder()
                                    .setAps(
                                        Aps.builder()
                                            .setSound("default")
                                            .setThreadId(notification.chatId.toString())
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                }
                .build()
        }

        /***
         * Logs the result of the notification send operation.
         * */
        return FirebaseMessaging
            .getInstance()
            .sendEach(messages)
            .toSendResult(notification.recipients)
    }

    /***
     * Converts a BatchResponse to a PushNotificationSendResult.
     *
     * @param allDeviceTokens: All device tokens for which the notification was sent.
     * @return The result of the notification send operation.
     * */
    private fun BatchResponse.toSendResult(
        allDeviceTokens: List<DeviceToken>
    ): PushNotificationSendResult {
        val succeeded = mutableListOf<DeviceToken>()
        val temporaryFailures = mutableListOf<DeviceToken>()
        val permanentFailures = mutableListOf<DeviceToken>()

        responses.forEachIndexed { index, sendResponse ->
            val deviceToken = allDeviceTokens[index]
            if (sendResponse.isSuccessful) {
                succeeded.add(deviceToken)
            } else {
                val errorCode = sendResponse.exception?.messagingErrorCode

                logger.warn("Failed to send notification to token ${deviceToken.token}: $errorCode")

                when (errorCode) {
                    MessagingErrorCode.UNREGISTERED,
                    MessagingErrorCode.SENDER_ID_MISMATCH,
                    MessagingErrorCode.INVALID_ARGUMENT,
                    MessagingErrorCode.THIRD_PARTY_AUTH_ERROR -> {
                        permanentFailures.add(deviceToken)
                    }

                    MessagingErrorCode.INTERNAL,
                    MessagingErrorCode.QUOTA_EXCEEDED,
                    MessagingErrorCode.UNAVAILABLE,
                    null -> {
                        temporaryFailures.add(deviceToken)
                    }
                }
            }
        }

        logger.debug(
            "Push notifications sent. Succeeded: ${succeeded.size}, " +
                    "temporary failures: ${temporaryFailures.size}, permanent failures: ${permanentFailures.size}"
        )

        return PushNotificationSendResult(
            succeeded = succeeded.toList(),
            temporaryFailures = temporaryFailures.toList(),
            permanentFailures = permanentFailures.toList(),
        )
    }
}