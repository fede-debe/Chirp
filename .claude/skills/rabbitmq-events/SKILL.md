---
name: rabbitmq-events
description: Use when modules need to communicate across boundaries asynchronously — defining a new domain event, publishing one, adding a RabbitMQ exchange/queue/binding, writing a @RabbitListener consumer, or wiring the message converter/retry behavior in this backend.
---

# RabbitMQ Domain Events

## Overview

Modules never call each other directly. A module **publishes a domain event** to a RabbitMQ
topic exchange; other modules **consume it from their own queue** and react independently. This
is the only sanctioned cross-module communication path (the other decoupling tool is data
mirroring — see [[chirp-architecture]]). For events handled *inside one module*, use Spring
application events instead — see [[spring-application-events]].

All event types and the broker wiring live in `common`, so publisher and consumer share the
exact same classes.

## Event type hierarchy

A base interface, one `sealed class` per domain area, `data class` subtypes:

```kotlin
// common/.../domain/events/ChirpEvent.kt
interface ChirpEvent {
    val eventId: String     // unique per event instance
    val eventKey: String    // routing key, e.g. "user.created"
    val occurredAt: Instant
    val exchange: String    // which exchange to publish to
}

// common/.../domain/events/user/UserEvent.kt
sealed class UserEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val exchange: String = UserEventConstants.USER_EXCHANGE,
    override val occurredAt: Instant = Instant.now(),
) : ChirpEvent {
    data class Created(
        val userId: UserId, val email: String, val username: String, val verificationToken: String,
        override val eventKey: String = UserEventConstants.USER_CREATED_KEY,
    ) : UserEvent()
    // Verified, RequestResendVerification, RequestResetPassword ...
}
```

Routing keys and exchange names are **constants in an `object`**, never inline strings:

```kotlin
object UserEventConstants {
    const val USER_EXCHANGE = "user.events"
    const val USER_CREATED_KEY = "user.created"
    const val USER_VERIFIED = "user.verified"
}
```

Queue names live in `common/.../infra/message_queue/MessageQueues.kt`:

```kotlin
object MessageQueues {
    const val NOTIFICATION_USER_EVENTS = "notification.user.events"
    const val CHAT_USER_EVENTS = "chat.user.events"
    const val NOTIFICATION_CHAT_EVENTS = "notification.chat.events"
}
```

Naming pattern: exchange `<area>.events`; routing key `<area>.<thing>`; queue
`<consumer>.<area>.events`.

## Publishing

Inject the shared `EventPublisher` (in `common`) and call `publish`. It routes by the event's
own `exchange` + `eventKey`, and **swallows-and-logs** failures so a broker hiccup doesn't break
the business transaction:

```kotlin
@Service
class AuthService(private val eventPublisher: EventPublisher, ...) {
    @Transactional
    fun register(...): User {
        val saved = userRepository.saveAndFlush(...).toUser()
        eventPublisher.publish(UserEvent.Created(saved.id, saved.email, saved.username, token.token))
        return saved
    }
}
```

```kotlin
@Component
class EventPublisher(private val rabbitTemplate: RabbitTemplate) {
    fun <T : ChirpEvent> publish(event: T) {
        try {
            rabbitTemplate.convertAndSend(event.exchange, event.eventKey, event)
            logger.info("Successfully published event: ${event.eventKey}")
        } catch (e: Exception) {
            logger.error("Failed to publish ${event.eventKey} event", e)
        }
    }
}
```

## Consuming

A `@RabbitListener` on a queue constant, in the consuming module's `infra/message_queue` (or
`infra/messaging`). Match on the sealed subtype with an exhaustive `when`; ignore irrelevant
subtypes with `else -> Unit`:

```kotlin
@Component
class NotificationUserEventListener(private val emailService: EmailService) {
    @RabbitListener(queues = [MessageQueues.NOTIFICATION_USER_EVENTS], containerFactory = "rabbitListenerContainerFactory")
    fun handleUserEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> emailService.sendVerificationEmail(event.email, event.username, event.userId, event.verificationToken)
            is UserEvent.RequestResetPassword -> emailService.sendPasswordResetEmail(...)
            else -> Unit
        }
    }
}
```

The listener receives the typed `UserEvent` directly — the message converter deserializes it
back into the sealed subtype.

## Broker wiring (RabbitMqConfig, in common)

`common/.../infra/message_queue/RabbitMqConfig.kt` declares **everything** centrally: the
message converter, template, listener container factory, exchanges, queues, and bindings.

- **Exchanges** are durable topic exchanges, one per area:
  `TopicExchange(UserEventConstants.USER_EXCHANGE, true, false)`.
- **Queues** are durable: `Queue(MessageQueues.NOTIFICATION_USER_EVENTS, true)`.
- **Bindings** connect a queue to an exchange with a routing pattern. Multiple queues can bind
  the same exchange (fan-out by interest):
  ```kotlin
  BindingBuilder.bind(notificationUserEventsQueue).to(userExchange).with("user.*")   // all user events
  BindingBuilder.bind(notificationChatEventsQueue).to(chatExchange).with(ChatEventConstants.CHAT_NEW_MESSAGE)  // one key
  ```
  `user.*` means both `notification` and `chat` queues receive every `user.*` event and each
  filters with its `when`.

### Current topology

```
user.events (exchange)
  ├─"user.*"─► notification.user.events  → send emails
  └─"user.*"─► chat.user.events          → create ChatParticipant mirror
chat.events (exchange)
  └─"chat.new_message"─► notification.chat.events → send push notifications
```

### Secure deserialization (do not weaken)

The Jackson (Jackson 3, `tools.jackson.*`) message converter uses a **whitelist** polymorphic
type validator — only `ChirpEvent` subtypes and standard collections can be deserialized. This
prevents RabbitMQ deserialization-gadget attacks. Keep new event types under the `ChirpEvent`
base so they pass the validator; don't broaden the allowlist.

```kotlin
BasicPolymorphicTypeValidator.builder()
    .allowIfBaseType(ChirpEvent::class.java)
    .allowIfSubType("java.util.")
    .allowIfSubType("kotlin.collections.")
    .build()
```

### Reliability

- Container factory is **transactional** (`setChannelTransacted(true)` + transaction manager),
  so consuming + DB work commit together.
- Retry/concurrency are configured in `application.yml` under
  `spring.rabbitmq.listener.simple` (retry with exponential backoff, `max-retries: 3`;
  `concurrency: 1` because the CloudAMQP free plan caps one consumer — raise on a paid plan).
  See [[configuration]].

## Adding a new event end-to-end

1. Add the routing-key/exchange constants to the area's `...EventConstants` object (or create
   one) in `common`.
2. Add a `data class` subtype to the area's sealed `…Event` class (or create the sealed class +
   queue constants in `MessageQueues`).
3. If it's a new exchange/queue/binding, declare the `@Bean`s in `RabbitMqConfig`.
4. Publish via `eventPublisher.publish(...)` from the producing service.
5. Add a `@RabbitListener` in each consuming module and handle the subtype.

## Common mistakes

- A feature module importing another to "call" it — publish/consume an event instead.
- Inline exchange/queue/routing strings instead of the shared constants.
- A new event type that doesn't extend `ChirpEvent` → blocked by the type validator at deserialize.
- Forgetting the binding `@Bean`, so the queue never receives the event.
- Letting `publish` throw into the business transaction — it catches and logs by design.
- Confusing this with in-process events — cross-module = RabbitMQ; same-module = [[spring-application-events]].
