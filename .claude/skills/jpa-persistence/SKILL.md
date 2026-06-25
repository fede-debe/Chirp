---
name: jpa-persistence
description: Use when adding or changing a JPA @Entity, a Spring Data repository, a database schema, an index, an entity relationship (ManyToOne/ManyToMany/JoinTable), fetch/cascade behavior, or wrapping data access in a transaction in this Postgres + Hibernate backend.
---

# JPA Persistence

## Overview

Persistence is JPA/Hibernate over PostgreSQL (Supabase-hosted). Each service owns its **own
Postgres schema**, all primary keys are **UUIDs**, entities are plain mutable classes (never
`data class`), and `infra/database` is the only layer that touches them. There are **no
migrations** — Hibernate `ddl-auto` manages schema (`update` in dev, `validate` in prod).

## Schema-per-service

Every entity declares its module's schema. This keeps each module's tables isolated and is
what makes the modular monolith's data boundaries real.

```kotlin
@Entity
@Table(name = "users", schema = "user_service", indexes = [...])
class UserEntity(...)
```

Schemas in use: `user_service`, `chat_service`, `notification_service`. A new module gets a new
`<name>_service` schema. Cross-schema joins are avoided — mirror data instead (see
[[chirp-architecture]]).

## Entities are plain classes, not data classes

```kotlin
@Entity
@Table(name = "users", schema = "user_service", indexes = [
    Index(name = "idx_users_email", columnList = "email"),
    Index(name = "idx_users_username", columnList = "username"),
])
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UserId? = null,                                  // nullable until persisted
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = false)
    var hashedPassword: String,
    @Column(nullable = false)
    var hasVerifiedEmail: Boolean = false,
    @CreationTimestamp var createdAt: Instant = Instant.now(),
    @UpdateTimestamp var updatedAt: Instant = Instant.now(),
)
```

Rules baked into that example:
- **Plain `class`, all properties `var`.** A `data class`'s generated `equals`/`hashCode`
  conflicts with Hibernate's identity/proxy expectations and corrupts lazy loading. Hard rule.
- **`var id: XId? = null`** — nullable because the ID doesn't exist until persisted. Map to a
  non-null model id with `id!!` after save (see [[domain-modeling]]).
- **UUID PKs** via `@GeneratedValue(strategy = GenerationType.UUID)` — no auto-increment
  integers (avoids leaking row counts, safe for distributed inserts). The one exception:
  `DeviceTokenEntity` uses `GenerationType.IDENTITY` (a `Long`) because tokens are
  high-churn rows with no need for a UUID.
- **`@Column(nullable = false, unique = true)`** declares constraints explicitly.
- **`@CreationTimestamp` / `@UpdateTimestamp`** (Hibernate) for audit times.
- **Indexes declared on `@Table`** with explicit names, matching real query patterns.

## Relationships

Use lazy fetching by default; reach for eager only when the association is always needed.

```kotlin
// ManyToOne — many messages to one chat. LAZY + a duplicated scalar FK column for cheap reads.
@Column(name = "chat_id", nullable = false, updatable = false)
var chatId: ChatId,                                   // scalar copy for queries without a join
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "chat_id", nullable = false, insertable = false, updatable = false)
@OnDelete(action = OnDeleteAction.CASCADE)            // DB-level cascade: delete chat → delete messages
var chat: ChatEntity? = null,

// ManyToOne sender — EAGER because every message render needs the sender, avoiding N+1 join-fetch.
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "sender_id", nullable = false)
var sender: ChatParticipantEntity,

// OneToMany children — cascade ALL + orphanRemoval so attachments live and die with the message.
@OneToMany(mappedBy = "chatMessage", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
var attachments: MutableList<ChatMessageAttachmentEntity> = mutableListOf()
```

ManyToMany uses an explicit join table with composite indexes in both column orders (so both
"who is in chat X" and "what chats is user X in" are fast):

```kotlin
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "chat_participants_cross_ref", schema = "chat_service",
    joinColumns = [JoinColumn(name = "chat_id")],
    inverseJoinColumns = [JoinColumn(name = "user_id")],
    indexes = [
        Index(name = "idx_chat_participant_chat_id_user_id", columnList = "chat_id,user_id", unique = true),
        Index(name = "idx_chat_participant_user_id_chat_id", columnList = "user_id,chat_id", unique = true),
    ],
)
var participants: Set<ChatParticipantEntity> = emptySet(),
```

> `@OnDelete(OnDeleteAction.CASCADE)` only adds the DB-level FK rule via Hibernate DDL. If the
> table/foreign key already exists (or is managed in Supabase), set the cascade action there
> too or it won't take effect.

## Repositories

Spring Data `JpaRepository<Entity, IdType>`. Prefer derived query methods; drop to `@Query`
only when needed.

```kotlin
interface UserRepository : JpaRepository<UserEntity, UserId> {
    fun findByEmail(email: String): UserEntity?
    fun findByEmailOrUsername(email: String, username: String): UserEntity?
}
```

- Use `findByIdOrNull(id)` (Spring Data Kotlin extension) instead of `findById(...).orElse(null)`.
- Custom finders that join/filter live as named methods (e.g. `findChatById(chatId, userId)`,
  `findByChatIdBefore(...)` for cursor pagination, `findLatestMessagesByChatIds(...)`).

## Transactions

`@Transactional` (Spring's `org.springframework.transaction.annotation`) goes on **service**
methods that perform multiple writes or need atomicity — not on repositories or controllers.

```kotlin
@Transactional
fun register(email: String, ...): User {
    val saved = userRepository.saveAndFlush(UserEntity(...)).toUser()   // flush to get the ID now
    val token = emailVerificationService.createVerificationToken(...)
    eventPublisher.publish(UserEvent.Created(...))                      // see rabbitmq-events
    return saved
}
```

- **`saveAndFlush`** when you need the generated ID *within the same transaction* (to build a
  child row's FK, or to put the ID on an outgoing event). Plain `save` otherwise.
- Events that must only fire after the data is durably committed use
  `@TransactionalEventListener(AFTER_COMMIT)` — see [[spring-application-events]].

## ddl-auto / migrations

`application-dev.yml` → `ddl-auto: update` (Hibernate evolves the schema). `application-prod.yml`
→ `ddl-auto: validate` (boot fails if the schema doesn't match the entities). There is **no
Flyway/Liquibase** — for a real production system you'd add one; until then, prod schema changes
must be applied manually so `validate` passes. See [[configuration]].

## Common mistakes

- `data class` entity, or `val` properties on an entity. → plain `class`, `var`.
- Forgetting `schema = "<x>_service"` on `@Table`.
- `EAGER` everywhere "to be safe" → N+1 and over-fetching. Default LAZY; justify EAGER.
- `@Transactional` on a controller or repository instead of the service method.
- Expecting `@OnDelete` cascade to work without the DB-level FK rule also being set.
- Using `save` then reading the generated ID in the same method — use `saveAndFlush`.
- Missing `kotlin("plugin.jpa")` in the module build file (no no-arg constructor) — see
  [[gradle-build-system]].
