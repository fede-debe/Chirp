---
name: spring-application-events
description: Use when components inside a single module need to react to something after a transaction commits — publishing an in-process Spring event with ApplicationEventPublisher and consuming it with @TransactionalEventListener (e.g. bridging a service write to a websocket broadcast).
---

# In-Process Spring Application Events

## Overview

For decoupling *within a single module*, this backend uses Spring's built-in
`ApplicationEventPublisher` and `@TransactionalEventListener` — not RabbitMQ. The defining
trait: listeners fire **after the database transaction commits** (`AFTER_COMMIT`), so observers
never act on data that might roll back.

Decision rule:
- **Across modules / async / survives restart →** RabbitMQ. See [[rabbitmq-events]].
- **Same module / in-memory reaction to a committed change →** Spring application events (this skill).

The canonical use here: a `chat` service writes to the DB and publishes an in-process event; the
`ChatWebSocketHandler` (same module) listens and broadcasts to connected clients only once the
write is durable. See [[websocket-realtime]].

## Event classes

Plain `data class`es in the module's `domain/event/` package (note: singular `event`, distinct
from the cross-module `domain/events/` in `common`). No base type, no annotations:

```kotlin
// chat/.../domain/event/ChatCreatedEvent.kt
data class ChatCreatedEvent(
    val chatId: ChatId,
    val participantIds: List<UserId>,
)
```

Examples in the codebase: `ChatCreatedEvent`, `ChatParticipantsJoinedEvent`,
`ChatParticipantLeftEvent`, `ChatDeletedEvent`, `MessageDeletedEvent`,
`ParticipantRemovedByAdminEvent`, `ProfilePictureUpdatedEvent`.

## Publishing

Inject `ApplicationEventPublisher` and publish, typically inside an `@Transactional` service
method right after the write:

```kotlin
@Service
class ChatService(
    private val applicationEventPublisher: ApplicationEventPublisher,
    ...
) {
    @Transactional
    fun createChat(creatorId: UserId, otherUserIds: Set<UserId>): Chat {
        return chatRepository.save(ChatEntity(...)).toChat(lastMessage = null).also { chat ->
            applicationEventPublisher.publishEvent(
                ChatCreatedEvent(chatId = chat.id, participantIds = chat.participants.map { it.userId })
            )
        }
    }
}
```

## Consuming — after commit

Listeners use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. This is the
whole point: the side effect (a websocket broadcast, here) runs only if the transaction
actually committed.

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onChatCreated(event: ChatCreatedEvent) {
    updateChatForUsers(chatId = event.chatId, userIds = event.participantIds)
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onDeleteMessage(event: MessageDeletedEvent) {
    broadcastToChat(event.chatId, OutgoingWebSocketMessage(type = MESSAGE_DELETED, payload = ...))
}
```

- Use `@TransactionalEventListener(AFTER_COMMIT)` (not plain `@EventListener`) whenever the
  reaction must not happen on rollback — which is the norm here.
- Multiple listeners can observe the same event; each `@TransactionalEventListener` method
  handles one event type.

## A subtlety: publishing outside a transaction

`@TransactionalEventListener` only fires when there's a transaction to bind to. `MessageDeletedEvent`
is published from `deleteMessage` (which is `@Transactional`), so its `AFTER_COMMIT` listener
runs. If you ever publish such an event from a **non-transactional** path, the `AFTER_COMMIT`
listener won't fire — that's why `ChatMessageService.deleteMessage` also calls
`messageCacheEvictionHelper.evictMessagesCache(...)` directly rather than relying solely on an
event. Keep publication inside the transactional method.

## Common mistakes

- Using a plain `@EventListener` when you need post-commit semantics → side effect runs even on
  rollback. Use `@TransactionalEventListener(AFTER_COMMIT)`.
- Publishing the event from outside a transaction and expecting `AFTER_COMMIT` to fire.
- Reaching for RabbitMQ for a same-module, in-memory reaction (overkill) — or, conversely, using
  an in-process event to talk to another module (it won't cross the boundary). See [[rabbitmq-events]].
- Putting these event classes in `common` — in-process events are module-local, in `domain/event/`.
