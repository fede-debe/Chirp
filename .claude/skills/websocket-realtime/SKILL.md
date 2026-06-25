---
name: websocket-realtime
description: Use when working on real-time features in this backend — the raw WebSocket handler, per-user session tracking, authenticating the WS handshake with JWT, broadcasting to chat participants, ping/pong keep-alive, typing indicators, or bridging domain events to websocket pushes.
---

# Real-Time WebSocket

## Overview

Real-time delivery uses **raw WebSocket** (not STOMP), one endpoint `/ws/chat`. A single
`TextWebSocketHandler` maintains in-memory maps of who's connected and which chats they're in,
guarded by a read/write lock, and fans messages out to the right sessions. Server-side state
changes reach clients by **listening to in-process domain events** and broadcasting — the handler
is the bridge between [[spring-application-events]] and connected clients.

## Wiring

```kotlin
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: ChatWebSocketHandler,
    @param:Value("\${chirp.web-socket.allowed-origin}") private val allowedOrigin: String,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws/chat").setAllowedOrigins(allowedOrigin)
    }
}
```

Allowed origin is environment config (`chirp.web-socket.allowed-origin`) — see [[configuration]].

## Handshake authentication

The same JWT as REST, sent as an `Authorization: Bearer` header on the upgrade request.
Validated in `afterConnectionEstablished`; no token → close the socket:

```kotlin
override fun afterConnectionEstablished(session: WebSocketSession) {
    val authHeader = session.handshakeHeaders.getFirst(HttpHeaders.AUTHORIZATION)
        ?: return session.close(CloseStatus.SERVER_ERROR.withReason("Authentication failed"))
    val userId = jwtService.getUserIdFromToken(authHeader)        // see security-and-auth
    ...
}
```

## Session state (concurrent maps + lock)

The handler holds four `ConcurrentHashMap`s, mutated together under a `ReentrantReadWriteLock`
so reads can be concurrent but a structural change is exclusive:

```kotlin
private val connectionLock = ReentrantReadWriteLock()
private val sessions        = ConcurrentHashMap<String, UserSession>()        // sessionId → session
private val userToSessions  = ConcurrentHashMap<UserId, MutableSet<String>>() // user → their sessionIds (multi-device)
private val userChatIds     = ConcurrentHashMap<UserId, MutableSet<ChatId>>() // user → chats they're in
private val chatToSessions  = ConcurrentHashMap<ChatId, MutableSet<String>>() // chat → sessionIds to broadcast to
```

- A user can have **multiple sessions** (multiple devices) — hence `userToSessions` is a set.
- On connect, the handler loads the user's chats (`chatService.findChatsByUser`) and registers
  the session under each chat. On disconnect it removes the session from every map.
- **Always** mutate these maps inside `connectionLock.write { }`, and snapshot before iterating
  for sends with `connectionLock.read { ... }`. Never touch them outside the lock.

## Message envelope (type + JSON payload)

Both directions use a two-field envelope: a `type` enum and a **`payload` that is itself a JSON
string**. The receiver switches on `type`, then deserializes `payload` into the matching DTO.
This lets the payload shape vary per type while parsing stays type-safe (important for the
Kotlin mobile client).

```kotlin
enum class IncomingWebSocketMessageType { NEW_MESSAGE, TYPING_STARTED, TYPING_STOPPED }
enum class OutgoingWebSocketMessageType {
    NEW_MESSAGE, MESSAGE_DELETED, PROFILE_PICTURE_UPDATED, CHAT_PARTICIPANTS_CHANGED,
    TYPING_INDICATOR, REMOVED_FROM_CHAT, CHAT_DELETED, ERROR
}
data class IncomingWebSocketMessage(val type: IncomingWebSocketMessageType, val payload: String)
data class OutgoingWebSocketMessage(val type: OutgoingWebSocketMessageType, val payload: String)
```

Incoming handling parses the envelope, then the payload by type:

```kotlin
override fun handleTextMessage(session, message) {
    val envelope = objectMapper.readValue(message.payload, IncomingWebSocketMessage::class.java)
    when (envelope.type) {
        NEW_MESSAGE -> handleSendMessage(objectMapper.readValue(envelope.payload, SendMessageDto::class.java), userSession.userId)
        TYPING_STARTED -> handleTypingEvent(session, dto.chatId, true)
        TYPING_STOPPED -> handleTypingEvent(session, dto.chatId, false)
    }
    // on JacksonException → send an ERROR envelope back, don't crash the socket
}
```

Outgoing broadcasts serialize a DTO into the payload, wrap it in an `OutgoingWebSocketMessage`,
and send the whole thing as JSON text.

## Events → broadcasts (the bridge)

The handler does **not** poll the DB. Services publish in-process domain events after their
transaction commits; the handler's `@TransactionalEventListener(AFTER_COMMIT)` methods broadcast
them. This guarantees clients only hear about committed changes. See [[spring-application-events]].

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onDeleteMessage(event: MessageDeletedEvent) =
    broadcastToChat(event.chatId, OutgoingWebSocketMessage(MESSAGE_DELETED, objectMapper.writeValueAsString(DeleteMessageDto(event.chatId, event.messageId))))
```

Handled events: `ChatCreatedEvent`, `ChatParticipantsJoinedEvent`, `ChatParticipantLeftEvent`,
`ChatDeletedEvent`, `MessageDeletedEvent`, `ParticipantRemovedByAdminEvent`,
`ProfilePictureUpdatedEvent` — each updates the maps and/or broadcasts.

Sending a chat message itself goes through the service (so it persists, evicts cache, and emits a
RabbitMQ `ChatEvent.NewMessage` for push) and is then broadcast to the chat's sessions:

```kotlin
private fun handleSendMessage(dto: SendMessageDto, senderId: UserId) {
    if (dto.chatId !in (userChatIds[senderId] ?: return)) return        // authorize: sender is in the chat
    val saved = chatMessageService.sendMessage(dto.chatId, senderId, dto.content, dto.messageId, dto.attachments)
    broadcastToChat(dto.chatId, OutgoingWebSocketMessage(NEW_MESSAGE, objectMapper.writeValueAsString(saved.toChatMessageDto())))
}
```

Client-generated `messageId` is honored so the client can match the echoed message to what it
sent (delivery confirmation). See [[jpa-persistence]] / [[caching-redis]] for the service side.

## Keep-alive (ping/pong)

A `@Scheduled` task pings every 30s and closes sessions that haven't ponged within 60s
(`@EnableScheduling` is on `ChirpApplication`). `handlePongMessage` records the last pong time.

```kotlin
companion object { private const val PING_INTERVAL_MS = 30_000L; private const val PONG_TIMEOUT_MS = 60_000L }
@Scheduled(fixedDelay = PING_INTERVAL_MS) fun pingClients() { /* ping open sessions, close timed-out ones */ }
```

## Typing indicators

`TYPING_STARTED/STOPPED` broadcast a `TYPING_INDICATOR` to the chat's *other* participants and
auto-stop after 3s via a `TaskScheduler` (`SchedulerConfig` provides a `ThreadPoolTaskScheduler`).
Timers are tracked per chat/user and cancelled on stop or disconnect.

## Common mistakes

- Mutating the session maps without `connectionLock.write { }`, or iterating without a `read`
  snapshot → race conditions.
- Broadcasting from a service directly instead of publishing an event the handler listens to.
- Using a plain `@EventListener` so broadcasts fire before commit — use `AFTER_COMMIT`.
- Forgetting handshake JWT validation, or skipping the per-message authorization
  (`chatId in userChatIds[user]`).
- Letting a bad inbound payload throw out of `handleTextMessage` instead of replying with an
  `ERROR` envelope.
