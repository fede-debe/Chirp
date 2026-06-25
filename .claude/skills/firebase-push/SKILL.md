---
name: firebase-push
description: Use when working on mobile push notifications in this backend — Firebase Cloud Messaging setup, sending FCM messages, Android/iOS platform config, device-token registration, classifying send failures, or the push retry queue.
---

# Push Notifications (Firebase)

## Overview

Push delivery uses Firebase Cloud Messaging via the Firebase Admin SDK. The SDK is initialized
once from a credentials JSON on the classpath; `FirebasePushNotificationService` sends to device
tokens (one-to-one, not topics) with platform-specific config; `PushNotificationService` owns
device-token CRUD, failure-driven token cleanup, and an in-memory retry queue. Pushes are
triggered by consuming a `ChatEvent.NewMessage` from RabbitMQ — see [[rabbitmq-events]].

## SDK initialization

Credentials JSON path comes from config; the SDK is initialized in `@PostConstruct` so it's
ready after the app starts:

```kotlin
@Service
class FirebasePushNotificationService(
    @param:Value("\${firebase.credentials-path}") private val credentialsPath: String,
    private val resourceLoader: ResourceLoader,
) {
    @PostConstruct
    fun initialize() {
        val serviceAccount = resourceLoader.getResource(credentialsPath)
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount.inputStream))
            .build()
        FirebaseApp.initializeApp(options)
    }
}
```

- Config: `firebase.credentials-path` =
  `classpath:firebase-credentials/chirp-firebase-adminsdk.json`. The file is bundled into the
  jar by the `bootJar` resource-copy from the `notification` module — see [[gradle-build-system]].
- In CI the JSON is written from a base64 secret before the build — see [[deployment]]. The
  credentials JSON is secret; never commit it.

## Sending — platform-specific config

Each recipient gets a `Message` with shared notification/data plus per-platform options. The
**collapse key / thread id is the chat id**, so multiple notifications for one chat collapse into
one on the device:

```kotlin
fun sendNotification(notification: PushNotification): PushNotificationSendResult {
    val messages = notification.recipients.map { recipient ->
        Message.builder()
            .setToken(recipient.token)
            .setNotification(Notification.builder().setTitle(notification.title).setBody(notification.message).build())
            .apply {
                notification.data.forEach { (k, v) -> putData(k, v) }
                when (recipient.platform) {
                    DeviceToken.Platform.ANDROID -> setAndroidConfig(
                        AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                            .setCollapseKey(notification.chatId.toString())
                            .setRestrictedPackageName("com.project.chirp").build())
                    DeviceToken.Platform.IOS -> setApnsConfig(
                        ApnsConfig.builder().setAps(Aps.builder().setSound("default")
                            .setThreadId(notification.chatId.toString()).build()).build())
                }
            }.build()
    }
    return FirebaseMessaging.getInstance().sendEach(messages).toSendResult(notification.recipients)
}
```

## Failure classification (critical)

The FCM batch response is split into three buckets by error code — this drives whether a token is
retried or deleted:

```kotlin
when (errorCode) {
    UNREGISTERED, SENDER_ID_MISMATCH, INVALID_ARGUMENT, THIRD_PARTY_AUTH_ERROR -> permanentFailures.add(token)  // delete token
    INTERNAL, QUOTA_EXCEEDED, UNAVAILABLE, null                                -> temporaryFailures.add(token)   // retry later
}
```

```kotlin
data class PushNotificationSendResult(
    val succeeded: List<DeviceToken>,
    val temporaryFailures: List<DeviceToken>,   // transient — retry
    val permanentFailures: List<DeviceToken>,   // dead token — remove from DB
)
```

**Permanent failures must trigger token deletion**; temporary failures are rescheduled.

## Device tokens & retry (PushNotificationService)

```kotlin
@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val firebasePushNotificationService: FirebasePushNotificationService,
) {
    @Transactional
    fun registerDevice(userId: UserId, token: String, platform: Platform): DeviceToken { ... }  // validates new tokens via FCM dry-run

    fun sendWithRetry(notification: PushNotification, attempt: Int = 0) {
        val result = firebasePushNotificationService.sendNotification(notification)
        result.permanentFailures.forEach { deviceTokenRepository.deleteByToken(it.token) }     // cleanup dead tokens
        if (result.temporaryFailures.isNotEmpty() && attempt < RETRY_DELAYS_SECONDS.size)
            scheduleRetry(notification.copy(recipients = result.temporaryFailures), attempt + 1)
    }
}
```

- **Token registration** validates a brand-new token with an FCM dry-run
  (`FirebaseMessaging.send(message, true)`); existing tokens just get re-pointed to the user.
  Tokens are unique (`idx_device_tokens_token`), `userId`-indexed, with a `platform` enum stored
  as STRING. See [[jpa-persistence]].
- **Retry queue** is an in-memory `ConcurrentSkipListMap<executeAtMillis, retries>` drained by a
  `@Scheduled(fixedDelay = 15s)` `processRetries`. Backoff: 30s, 60s, 120s, 300s, 600s (5
  attempts); anything older than 30 minutes is dropped. This queue is **per-instance and not
  durable** — fine for best-effort push, but it doesn't survive a restart.
- Push fan-out **excludes the sender** (`filter { it.userId != senderUserId }`).

## Triggered by

`NotificationChatEventListener` consumes `ChatEvent.NewMessage` from RabbitMQ and calls
`sendNewMessageNotifications(...)`. The chat module publishes that event when a message is sent.
See [[rabbitmq-events]].

## Common mistakes

- Not deleting tokens on permanent failure → repeatedly pushing to dead devices.
- Treating all failures the same (retrying permanent ones, dropping transient ones).
- Committing the Firebase credentials JSON, or forgetting the CI base64-decode / `bootJar`
  resource copy so the file is missing at runtime (the app throws on init).
- Sending the push back to the message's sender.
- Assuming the retry queue is durable — it's in-memory and per-instance.
