# Chirp — Architecture & Structure Reference

> Blueprint document. Captures the full setup so it can be replicated for future projects with different features but
> the same infrastructure foundation.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Module Layout](#2-module-layout)
3. [Build System](#3-build-system)
4. [Infrastructure & External Services](#4-infrastructure--external-services)
5. [Security](#5-security)
6. [Database Design](#6-database-design)
7. [Event-Driven Messaging (RabbitMQ)](#7-event-driven-messaging-rabbitmq)
8. [Caching (Redis)](#8-caching-redis)
9. [Real-Time Communication (WebSocket)](#9-real-time-communication-websocket)
10. [Push Notifications (Firebase)](#10-push-notifications-firebase)
11. [File Storage (Supabase)](#11-file-storage-supabase)
12. [Email (Mailgun)](#12-email-mailgun)
13. [API Surface](#13-api-surface)
14. [Module Deep-Dives](#14-module-deep-dives)
15. [Configuration & Environments](#15-configuration--environments)
16. [Blueprint Checklist for New Projects](#16-blueprint-checklist-for-new-projects)

---

## 1. Project Overview

Chirp is a real-time chat backend built with Kotlin + Spring Boot. It demonstrates a production-ready multi-module
architecture with:

- JWT-based stateless authentication
- Real-time messaging over WebSocket
- Async event propagation via RabbitMQ
- Push notifications via Firebase Cloud Messaging
- Profile picture uploads via Supabase Storage
- Email delivery via Mailgun
- Redis caching for hot data

**Language / Runtime:** Kotlin (JVM 21), Spring Boot 4.0.2
**Architecture style:** Modular monolith (all modules are compiled together into a single deployable)

---

## 2. Module Layout

```
chirp/
├── app/              # Entry point. Wires all modules together. Holds SecurityConfig and RedisConfig.
├── user/             # Authentication: register, login, token refresh, email verification, password reset
├── chat/             # Chat rooms, messages, participants, profile pictures, WebSocket handler
├── notification/     # Email delivery, Firebase push notifications, device token management
├── common/           # Shared: JWT service, RabbitMQ infra, domain events, type-safe IDs, exception handling
└── build-logic/      # Gradle convention plugins (shared build configuration)
```

### Dependency graph

```
app ──► user
    ──► chat
    ──► notification
    ──► common

user         ──► common
chat         ──► common
notification ──► common
```

`common` has no internal module dependencies — it is a pure library.

---

## 3. Build System

### Gradle multi-module with version catalog

All dependency versions are centralized in `gradle/libs.versions.toml`. Modules reference them as `libs.some.dep` —
never hardcode versions inside `build.gradle.kts` files.

### Convention plugins (`build-logic/`)

Three layered plugins eliminate boilerplate across modules:

| Plugin                      | Applies to                                         | What it does                                                                                                                                    |
|-----------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `chirp.kotlin-common`       | All modules                                        | Kotlin JVM + Spring Kotlin + Spring Dependency Management. Sets JVM toolchain to Java 21. Forces patched transitive dep versions (Netty, gRPC). |
| `chirp.spring-boot-service` | Feature modules (user, chat, notification, common) | Extends kotlin-common. Adds web starter, test dependencies. Enables JPA all-open.                                                               |
| `chirp.spring-boot-app`     | `app` module only                                  | Extends spring-boot-service. Adds the Spring Boot plugin so the fat jar is built here.                                                          |

**Key point:** Only the `app` module produces an executable jar. Feature modules produce plain libraries.

### Building the executable jar

```bash
./gradlew :app:bootJar
```

Output: `app/build/libs/app-<version>.jar`

The `bootJar` task in `app/build.gradle.kts` is configured to pull resources from feature modules into the jar root:

```kotlin
tasks {
    named<BootJar>("bootJar") {
        from(project(":notification").projectDir.resolve("src/main/resources")) {
            into("")
        }
        from(project(":user").projectDir.resolve("src/main/resources")) {
            into("")
        }
    }
}
```

This is required because:

- `notification` resources include the Firebase Admin SDK credentials JSON (`firebase-credentials/`)
- `user` resources include email templates and any module-specific config

Without this, those classpath resources would be missing from the fat jar and the app would fail to start (Firebase
initialization would throw, email templates would 404).

**For new projects:** repeat this pattern for any feature module whose `src/main/resources` contains files that need to
be on the classpath at runtime (credentials, templates, etc.).

### Forcing transitive dependency versions

Security patches for transitive vulnerabilities are forced in `chirp.kotlin-common.gradle.kts` so they apply
project-wide without repeating them in each module.

---

## 4. Infrastructure & External Services

All infrastructure is cloud-hosted. There are no Docker files or local infrastructure configs — everything is wired via
environment variables.

| Service            | Provider                 | Used for                | Env vars                              |
|--------------------|--------------------------|-------------------------|---------------------------------------|
| PostgreSQL         | Supabase                 | Primary database        | `POSTGRES_PASSWORD`                   |
| Redis              | RedisLabs (europe-west1) | Caching                 | `REDIS_PASSWORD`                      |
| Message queue      | CloudAMQP (RabbitMQ)     | Async event propagation | `RABBITMQ_PASSWORD`                   |
| File storage       | Supabase Storage         | Profile pictures        | `SUPABASE_SERVICE_KEY`                |
| Push notifications | Firebase Cloud Messaging | Mobile push             | Firebase credentials JSON (classpath) |
| Email              | Mailgun SMTP             | Transactional email     | `MAILGUN_PASSWORD`                    |
| JWT signing        | n/a                      | Token signing           | `JWT_SECRET_BASE64`                   |

### What you need to provision for a new project

1. **Supabase project** → gives you PostgreSQL + Storage in one place
2. **RedisLabs free cluster** (or any Redis host)
3. **CloudAMQP free plan** (or any RabbitMQ host) — note: free plan caps concurrency at 1
4. **Firebase project** → download the Admin SDK service account JSON
5. **Mailgun account** → SMTP credentials
6. A base64-encoded JWT secret (generate with `openssl rand -base64 64`)

---

## 5. Security

### JWT flow

```
POST /api/auth/login
  → returns { accessToken, refreshToken }

Every subsequent request:
  Authorization: Bearer <accessToken>
  → JwtAuthFilter extracts userId, sets SecurityContext

POST /api/auth/refresh
  → validates refreshToken (hashed in DB), returns new accessToken
```

**Access token:** Short-lived (15 min prod, longer in dev). Stateless — no DB lookup needed per request.
**Refresh token:** 30 days. SHA-256 hashed before storage. Invalidated on logout.

### Spring Security config (in `app` module)

- Stateless sessions (no HttpSession)
- CSRF disabled (JWT-based)
- Public routes: `/api/auth/**` (except `/api/auth/change-password`)
- Everything else requires authentication
- 401 returned for unauthenticated requests (no redirect)

### JwtAuthFilter (in `user` module)

Located in the filter chain before `UsernamePasswordAuthenticationFilter`. Extracts `userId` from the JWT `subject`
claim and stores it in the `SecurityContext` so any downstream code can call `requestUserId` to get the current user.

### Rate limiting

Two independent limiters — both are in-memory (not Redis-backed), disabled in dev:

| Limiter          | Scope             | Limit                                |
|------------------|-------------------|--------------------------------------|
| IpRateLimiter    | Per IP address    | 10 requests / hour on auth endpoints |
| EmailRateLimiter | Per email address | Prevents verification/reset spam     |

**Note for new projects:** These are custom implementations. If you need distributed rate limiting (multiple replicas),
replace with a Redis-backed solution.

---

## 6. Database Design

### Schemas

Chirp uses **separate PostgreSQL schemas** per service to keep data isolated:

- `user_service` schema → all user/auth tables
- `chat_service` schema → all chat tables

This is configured via `@Table(schema = "user_service", ...)` on JPA entities.

### User service tables

| Table                       | Key columns                                                     | Notes                                      |
|-----------------------------|-----------------------------------------------------------------|--------------------------------------------|
| `users`                     | id (UUID PK), email, username, hashedPassword, hasVerifiedEmail | Indexes on email and username for lookup   |
| `refresh_tokens`            | id, userId (FK), hashedToken, expiresAt                         | SHA-256 hashed — never store raw tokens    |
| `email_verification_tokens` | id, token (unique), userId (FK), expiresAt, usedAt              | One-time use; mark used rather than delete |
| `password_reset_tokens`     | id, token (unique), userId (FK), expiresAt, usedAt              | Same pattern as verification tokens        |

### Chat service tables

| Table                         | Key columns                                                  | Notes                                       |
|-------------------------------|--------------------------------------------------------------|---------------------------------------------|
| `chat_participants`           | userId (UUID PK), username, email, profilePictureUrl         | Mirror of user data for query efficiency    |
| `chats`                       | id (UUID PK), creatorId (FK), createdAt                      |                                             |
| `chat_participants_cross_ref` | chatId, participantId                                        | Many-to-many join table                     |
| `chat_messages`               | id (UUID PK), chatId (FK), senderId (FK), content, createdAt | Index on (chatId, createdAt) for pagination |

### ID strategy

All primary IDs are **UUID v4**. No auto-increment integers. This matters for distributed inserts and avoids leaking
record counts.

### Type-safe ID wrappers (in `common`)

`UserId`, `ChatId`, `ChatMessageId` are inline/value classes wrapping `UUID`. This prevents accidentally passing a
`ChatId` where a `UserId` is expected — caught at compile time.

### No migrations

Chirp uses Hibernate `ddl-auto: update` (dev) and `validate` (prod). For a production project you should add **Flyway**
or **Liquibase** — schema changes managed by Hibernate in prod is risky.

---

## 7. Event-Driven Messaging (RabbitMQ)

### Why

Modules don't call each other directly. Instead, a module publishes a domain event to RabbitMQ, and other modules
consume it independently. This decouples user registration from email sending and chat participant syncing.

### Topology

```
USER_EXCHANGE (topic)
  user.created     → notification: send verification email
                   → chat: create ChatParticipant mirror
  user.verified    → notification: (future use)
  user.resend_ver  → notification: resend verification email
  user.reset_pass  → notification: send password reset email

CHAT_EXCHANGE (topic)
  chat.new_message → notification: send push notification to recipients
```

### Publishing events

`EventPublisher` (in `common`) wraps `RabbitTemplate`. Any service that needs to emit an event injects `EventPublisher`
and calls:

```kotlin
eventPublisher.publish(UserEvent.Created(userId, email, username, token))
```

### Consuming events

Listeners are annotated with `@RabbitListener(queues = [MessageQueues.NOTIFICATION_USER_EVENTS])`. Each module has its
own listener file.

### Message serialization

Jackson JSON with **secure polymorphic type validation** — only whitelisted event types are deserializable. This
prevents RabbitMQ deserialization gadget attacks.

### CloudAMQP free plan limitations

- Max 1 concurrent consumer per queue
- This is why `concurrency = 1` is set on listener containers
- For higher throughput, upgrade the plan or switch brokers

---

## 8. Caching (Redis)

### Configuration

`RedisCacheManager` with Lettuce driver (non-blocking). Two cache TTLs:

- Default: 1 hour
- `messages` cache: 30 minutes

### What is cached

`ChatService.getChatMessages()` is cached in Redis under the `messages` cache. Cache key includes `chatId`, `before`
cursor, and `pageSize`.

**Only the first page is cached** (pageSize ≤ 50 and no cursor) — subsequent pages are always fetched from the database.

### Serialization

Jackson-based, with secure polymorphic type handling (same whitelist approach as RabbitMQ).

### Transaction awareness

`transactionAware = true` — data is only written to the cache after the database transaction commits successfully.
Prevents caching uncommitted data.

---

## 9. Real-Time Communication (WebSocket)

### Endpoint

`ws://host/ws/chat` — raw WebSocket (not STOMP).

### Authentication

JWT passed as `Authorization: Bearer <token>` header during the WebSocket upgrade handshake. Validated before the
connection is accepted.

### Message types (server → client)

| Type                        | Trigger                                     |
|-----------------------------|---------------------------------------------|
| `NEW_MESSAGE`               | A new chat message is sent                  |
| `MESSAGE_DELETED`           | A message is deleted                        |
| `CHAT_PARTICIPANTS_CHANGED` | Someone joins or leaves a chat              |
| `PROFILE_PICTURE_UPDATED`   | A participant updates their profile picture |
| `ERROR`                     | Any error condition                         |

### Keep-alive

Server sends a ping every 30 seconds. Connection is dropped if no pong received within 60 seconds.

### How it works internally

`ChatWebSocketHandler` maintains a map of `userId → WebSocketSession`. When an event happens (new message, delete,
etc.), the handler looks up all affected users' sessions and sends the payload directly.

---

## 10. Push Notifications (Firebase)

### Setup

1. Create a Firebase project
2. Enable Cloud Messaging
3. Download the Admin SDK service account JSON → place at `classpath:firebase-credentials/<name>.json`
4. Set `firebase.credentials-path=classpath:firebase-credentials/<name>.json` in config

### How device tokens work

- Mobile client registers a FCM device token with the backend on app launch
- Backend stores `DeviceToken(userId, token, platform)` in the DB
- When a notification needs to be sent, all tokens for a user are fetched and targeted

### Platform-specific config

| Platform | Config applied                                                                                         |
|----------|--------------------------------------------------------------------------------------------------------|
| Android  | `AndroidConfig` with HIGH priority + collapse key (collapses multiple notifications per chat into one) |
| iOS      | `ApnsConfig` with sound="default" + threadId (same collapse behavior)                                  |

### Failure classification

`PushNotificationSendResult` splits responses into:

- `succeeded` — delivered
- `temporaryFailures` — retry later (quota, server error, unavailable)
- `permanentFailures` — delete token (unregistered, invalid, sender mismatch)

Permanent failures should trigger token cleanup in the DB.

---

## 11. File Storage (Supabase)

### Flow for profile pictures

```
1. Client calls POST /api/participants/profile-picture-upload?mimeType=image/jpeg
   → Backend generates a signed Supabase upload URL + token
   → Returns { uploadUrl, token } to client

2. Client uploads file directly to Supabase Storage using the signed URL
   (binary upload, never touches your backend)

3. Client calls POST /api/participants/confirm-profile-picture
   → Backend confirms the upload, generates the public URL
   → Stores URL in ChatParticipant entity
   → Broadcasts PROFILE_PICTURE_UPDATED event to WebSocket subscribers
```

### Supabase REST client

`SupabaseRestClientConfig` creates a Spring `RestClient` bean pre-configured with:

- Base URL: Supabase project URL
- `Authorization: Bearer <SUPABASE_SERVICE_KEY>` header
- `Content-Type: application/json`

---

## 12. Email (Mailgun)

### Templates

Thymeleaf HTML templates under `resources/templates/emails/`:

- `account-verification.html` — sent on registration and resend-verification
- `reset-password.html` — sent on forgot-password

### Sending flow

1. RabbitMQ delivers a `UserEvent` to `NotificationUserEventListener`
2. Listener calls `EmailService.sendVerificationEmail()` or `sendPasswordResetEmail()`
3. `EmailTemplateService` processes the Thymeleaf template with dynamic variables
4. `EmailService` uses Spring's `JavaMailSender` to send via Mailgun SMTP

### SMTP config

- Host: `smtp.mailgun.org`, port `587`
- STARTTLS enabled
- Credentials: username + `${MAILGUN_PASSWORD}`

---

## 13. API Surface

### Authentication (`/api/auth`)

| Method | Path                   | Auth | Notes                    |
|--------|------------------------|------|--------------------------|
| POST   | `/register`            | No   | IP rate limited          |
| POST   | `/login`               | No   | IP rate limited          |
| POST   | `/refresh`             | No   | IP rate limited          |
| POST   | `/logout`              | Yes  |                          |
| POST   | `/resend-verification` | No   | Email rate limited       |
| GET    | `/verify`              | No   | `?token=...` query param |
| POST   | `/forgot-password`     | No   | IP rate limited          |
| POST   | `/reset-password`      | No   |                          |
| POST   | `/change-password`     | Yes  | IP rate limited          |

### Chat (`/api/chat`)

| Method | Path                 | Notes                                              |
|--------|----------------------|----------------------------------------------------|
| GET    | `/`                  | All chats for current user                         |
| GET    | `/{chatId}`          |                                                    |
| GET    | `/{chatId}/messages` | `?before=<cursor>&pageSize=<n>` — cursor paginated |
| POST   | `/`                  | Create chat                                        |
| POST   | `/{chatId}/add`      | Add participants                                   |
| DELETE | `/{chatId}/leave`    | Leave chat                                         |

### Participants (`/api/participants`)

| Method | Path                       | Notes                                                  |
|--------|----------------------------|--------------------------------------------------------|
| GET    | `/`                        | `?query=username_or_email` or current user if no query |
| POST   | `/profile-picture-upload`  | `?mimeType=image/jpeg` — returns signed upload URL     |
| POST   | `/confirm-profile-picture` | Finalize upload                                        |
| DELETE | `/profile-picture`         |                                                        |

### Messages (`/api/messages`)

| Method | Path           | Notes |
|--------|----------------|-------|
| DELETE | `/{messageId}` |       |

### Notifications (`/api/notification`)

| Method | Path        | Notes                     |
|--------|-------------|---------------------------|
| POST   | `/register` | Register FCM device token |
| DELETE | `/{token}`  | Unregister device token   |

---

## 14. Module Deep-Dives

### `common`

The foundation. Every other module depends on it. Contains:

- **JwtService** — token generation and validation. Shared so any module can verify tokens without depending on `user`.
- **EventPublisher** — RabbitMQ publish abstraction. Accepts any `ChirpEvent`.
- **RabbitMqConfig** — declares exchanges, queues, bindings, message converter. One central place.
- **UserEvent / ChatEvent** — sealed classes defining all event shapes. Both publisher and consumer use the same types.
- **Type-safe IDs** — `UserId`, `ChatId`, `ChatMessageId`.
- **CommonExceptionHandler** — `@RestControllerAdvice` catching `UnauthorizedException`, `ForbiddenException`,
  `InvalidTokenException`, and validation errors.
- **`requestUserId`** — extension property on `HttpServletRequest` (or similar) that extracts the authenticated user ID
  from the `SecurityContext`.

### `user`

Owns everything related to identity:

- Registration, login, token refresh, logout
- Email verification (token issued → email sent via event → token validated via GET endpoint)
- Password reset (same token pattern)
- Rate limiting interceptors (IP + email based)

### `chat`

Owns real-time messaging:

- Chat rooms (create, list, leave)
- Messages (send via WebSocket, delete via REST, paginated history via REST)
- Participant management (add, sync from user events)
- Profile pictures (Supabase upload flow)
- WebSocket handler (connection management, message fanout)

**Important:** `ChatParticipant` is a local **mirror** of user data. When a user registers, the `user.created` event
causes `chat` to create its own `ChatParticipant` row. This avoids cross-module database joins.

### `notification`

Owns outbound communication:

- Email (Mailgun via SMTP)
- Push notifications (Firebase)
- Device token CRUD

Consumes events from both `USER_EXCHANGE` (for emails) and `CHAT_EXCHANGE` (for push notifications). Never calls user or
chat modules directly.

### `app`

Only wires things together:

- `ChirpApplication` — `@SpringBootApplication` + `@EnableScheduling`
- `SecurityConfig` — Spring Security filter chain
- `RedisConfig` — `RedisCacheManager` setup

No business logic lives here.

---

## 15. Configuration & Environments

### Files

| File                   | Purpose                                   |
|------------------------|-------------------------------------------|
| `application.yml`      | Base config — applies to all environments |
| `application-dev.yml`  | Development overrides                     |
| `application-prod.yml` | Production overrides                      |

Activate with `--spring.profiles.active=dev` or `prod`.

### Key differences: dev vs prod

| Setting              | Dev                             | Prod                                      |
|----------------------|---------------------------------|-------------------------------------------|
| `hibernate.ddl-auto` | `update` (auto-migrates schema) | `validate` (fail if schema doesn't match) |
| IP rate limiting     | disabled                        | enabled                                   |
| JWT access token TTL | longer (e.g. 1000 min)          | 15 min                                    |
| HTTPS                | not required                    | required                                  |

### Environment variables required at runtime

```
POSTGRES_PASSWORD
REDIS_PASSWORD
RABBITMQ_PASSWORD
MAILGUN_PASSWORD
JWT_SECRET_BASE64
SUPABASE_SERVICE_KEY
```

Firebase credentials are loaded from the classpath (bundled in the jar), not from env vars.

---

## 16. Blueprint Checklist for New Projects

Use this when starting a new project with the same stack.

### Gradle setup

- [ ] Create `build-logic/` with `chirp.kotlin-common`, `chirp.spring-boot-service`, `chirp.spring-boot-app` convention
  plugins
- [ ] Create `gradle/libs.versions.toml` version catalog
- [ ] Set up `settings.gradle.kts` with module includes and version catalog reference
- [ ] Only the `app` module gets `chirp.spring-boot-app` — all others get `chirp.spring-boot-service`

### Modules to create

- [ ] `common` — JWT, RabbitMQ config, events, type-safe IDs, exception handling
- [ ] `app` — entry point, SecurityConfig, RedisConfig
- [ ] Feature modules (replace user/chat/notification with your domain)

### Infrastructure to provision

- [ ] Supabase project (PostgreSQL + Storage)
- [ ] RedisLabs cluster
- [ ] CloudAMQP instance
- [ ] Firebase project + Admin SDK JSON
- [ ] Mailgun account

### Security baseline

- [ ] JWT secret generated and stored as env var (base64 encoded)
- [ ] `SecurityConfig` with stateless sessions, CSRF disabled, public/private route split
- [ ] `JwtAuthFilter` in filter chain
- [ ] Refresh token hashing (SHA-256) before DB storage
- [ ] Rate limiting on sensitive endpoints

### Database

- [ ] Separate schemas per service
- [ ] UUID PKs everywhere
- [ ] Add Flyway or Liquibase (Chirp doesn't have this — you should)
- [ ] Mirror user data into other service schemas to avoid cross-service joins

### RabbitMQ events

- [ ] Define sealed class event hierarchy in `common`
- [ ] One exchange per domain area (topic type)
- [ ] Secure polymorphic type whitelist in Jackson message converter
- [ ] Retry config on listener containers (exponential backoff)

### Caching

- [ ] `RedisCacheManager` with TTL per cache name
- [ ] `transactionAware = true`
- [ ] Secure Jackson serializer for cache values

### Push notifications

- [ ] Firebase Admin SDK credential file on classpath
- [ ] `@PostConstruct` initialization in service
- [ ] Device token table with platform column (ANDROID/IOS)
- [ ] Permanent failure cleanup logic

### Configuration

- [ ] `application.yml` (base) + `application-dev.yml` + `application-prod.yml`
- [ ] `ddl-auto: update` in dev, `validate` in prod
- [ ] All secrets via env vars — nothing hardcoded
