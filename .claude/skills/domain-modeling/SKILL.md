---
name: domain-modeling
description: Use when modeling a concept in this backend — creating a domain model, choosing how a thing is represented across the persistence/domain/API layers, adding a type-safe ID, writing mapper functions between entity/model/DTO, or defining a sealed hierarchy.
---

# Domain Modeling

## Overview

Every concept exists in up to **three representations**, each owned by a different layer, with
plain Kotlin **mapper functions** translating between them. This keeps the database shape, the
business shape, and the wire shape independent so each can change without breaking the others.

```
JPA Entity (infra)  ──toX()──►  Domain Model (domain)  ──toXDto()──►  DTO (api)
   var, mutable                  data class, immutable               data class, immutable
   Hibernate-managed             business logic operates here        request/response shape
```

## The three representations

| Representation | Layer    | Kind          | Purpose                                    |
|----------------|----------|---------------|--------------------------------------------|
| `XEntity`      | `infra/database/entities` | plain `class`, `var` | DB table mapping (Hibernate). See [[jpa-persistence]]. |
| `X` (model)    | `domain/models`           | `data class`         | What business logic works with. Framework-free. |
| `XDto`         | `api/dto`                 | `data class`         | Request/response payload. See [[rest-api]]. |

Not every concept needs all three. Small read models may skip the DTO; some events carry data
without an entity. But **entities never leave `infra`** and **DTOs never enter `service`** —
the domain model is the lingua franca in between.

Example — the `User` concept:

```kotlin
// domain/model/User.kt — what services pass around
data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val hasEmailVerified: Boolean,
    val typingIndicatorsEnabled: Boolean,
)
```

## Mapper functions

Mappers are **extension functions** named `toX()`, grouped in a file per direction/layer.
They're pure functions, not Spring beans.

- Entity → model: `infra/database/mappers/` (e.g. `UserEntity.toUser()`)
- Model → DTO: `api/mappers/` (e.g. `User.toUserDto()`, `Chat.toChatDto()`)

```kotlin
// infra/database/mappers/UserMappers.kt
fun UserEntity.toUser(): User = User(
    id = id!!,                       // id is non-null after persistence
    email = email,
    username = username,
    hasEmailVerified = hasVerifiedEmail,
    typingIndicatorsEnabled = typingIndicatorsEnabled,
)
```

```kotlin
// api/mappers/ChatDtoMappers.kt
fun Chat.toChatDto(): ChatDto = ChatDto(
    id = id,
    participants = participants.map { it.toChatParticipantDto() },
    lastActivityAt = lastActivityAt,
    lastMessage = lastMessage?.toChatMessageDto(),
    creator = creator.toChatParticipantDto(),
)
```

Mappers compose: a `Chat.toChatDto()` calls `ChatParticipant.toChatParticipantDto()` and
`ChatMessage.toChatMessageDto()` on its nested pieces.

## Type-safe IDs

IDs are **`typealias`es over `UUID`**, declared in `common/domain/type/`, shared by every
module:

```kotlin
// common/.../domain/type/UserId.kt
typealias UserId = UUID
typealias ChatId = UUID
typealias ChatMessageId = UUID
```

Why a typealias rather than `UUID` everywhere: a single place to change the underlying type,
and signatures read as intent (`fun getChatById(chatId: ChatId, requestUserId: UserId)`).

> Trade-off to know: a `typealias` is **not** a distinct compile-time type — `UserId` and
> `ChatId` are interchangeable to the compiler, so it documents intent but won't *catch*
> passing one where the other is expected. (A value/inline class would enforce it but adds
> friction with JPA and Jackson; this project chose the lightweight typealias.) Add new IDs to
> `common` so all modules can reference them.

## Sealed hierarchies

Use `sealed class` for a closed set of related shapes that get exhaustively handled — chiefly
**events**. Subtypes are `data class`es. The exhaustive `when` (no `else`, or `else -> Unit`
for partial handlers) is the payoff: add a subtype and the compiler flags every consumer.

```kotlin
sealed class UserEvent(...) : ChirpEvent {
    data class Created(val userId: UserId, val email: String, ...) : UserEvent()
    data class Verified(val userId: UserId, ...) : UserEvent()
}
```

See [[rabbitmq-events]] for the event hierarchy and [[spring-application-events]] for
in-process event classes.

## Domain exceptions

Domain failures are `RuntimeException` subclasses in `domain/exception/`, with a fixed
message, thrown by services and translated to HTTP by a `@RestControllerAdvice`:

```kotlin
class ForbiddenException : RuntimeException("You are not allowed to do that")
```

See [[rest-api]] for the exception→HTTP mapping convention.

## Common mistakes

- Returning a JPA entity (or accepting a DTO) in a service signature. Map to/from the domain
  model at the boundary.
- Putting framework annotations on a domain model. Domain stays pure Kotlin.
- Defining a type-safe ID inside a feature module. It belongs in `common` so others can use it.
- Making a mapper a `@Component`. Mappers are plain extension functions.
- Reaching for `data class` on an entity. Entities are plain `class`es — see [[jpa-persistence]].
