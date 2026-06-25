---
name: chirp-architecture
description: Use when adding a feature, module, endpoint, entity, or event to this Kotlin + Spring Boot modular-monolith backend, or when deciding which module owns code and which layer (api/service/domain/infra) it belongs in. Entry point that routes to the topic skills.
---

# Modular-Monolith Architecture

## Overview

This backend is a **modular monolith**: several Gradle modules compiled together into a
single deployable jar. Modules are isolated by responsibility and never call each other
directly — they communicate through asynchronous events. Only the `app` module is
runnable; every feature module is a plain library.

This is the **hub skill**. It defines module boundaries and layering, then routes you to
the focused skill for whatever you're building.

## Module map

| Module         | Role                                                                 | Runnable |
|----------------|----------------------------------------------------------------------|----------|
| `app`          | Entry point. Wires modules, holds `SecurityConfig` + `RedisConfig`. No business logic. | yes (`bootJar`) |
| `common`       | Shared library: JWT service, event base types, RabbitMQ config, type-safe IDs, base exceptions. Depends on nothing. | no |
| feature module | One bounded domain (here: `user`, `chat`, `notification`). Depends only on `common`. | no |

### The dependency rule (never violate)

```
app ──► every feature module + common
feature module ──► common         (ONLY)
common ──► nothing
```

- A feature module **must not** depend on another feature module. If `chat` needs user
  data, it keeps its own mirror copy (see "Cross-module communication" below).
- `common` is a pure library — it must not depend on any feature module.
- Enforced by hand in each `build.gradle.kts` via `implementation(projects.common)`.

### One base package across all modules

Every module places code under the **same** root package `com.project.chirp.*` (just
different sub-packages). This is deliberate: the single `@SpringBootApplication` in
`app/.../ChirpApplication.kt` lives at `com.project.chirp`, so Spring's component scan
picks up `@Component`/`@Service`/`@Configuration`/`@RestController` beans from *all*
modules automatically. A new module's code must sit under `com.project.chirp` or its
beans will silently not load.

## The four-layer package structure (every feature module)

Each module repeats this internal layout. Dependencies point inward: `api` → `service` →
`domain`; `infra` implements outward adapters that `service` depends on.

```
<module>/src/main/kotlin/com/project/chirp/
├── api/            # HTTP/WS boundary — never contains business logic
│   ├── controllers/      @RestController classes
│   ├── dto/              request/response DTOs (+ dto/ws for websocket envelopes)
│   ├── mappers/          domain model → DTO extension functions
│   ├── exception_handling/  @RestControllerAdvice per module
│   ├── config/           filters, interceptors, WebMvc config
│   └── websocket/        handlers + websocket config
├── service/        # business logic. @Service classes. @Transactional lives here.
├── domain/         # pure Kotlin, no framework annotations
│   ├── models/           domain models (data classes)
│   ├── event/            in-process Spring events (this module only)
│   ├── exception/        domain exceptions (RuntimeException subclasses)
│   └── type/             — (type-safe IDs live in common)
└── infra/          # adapters to the outside world
    ├── database/entities/       JPA @Entity classes
    ├── database/repositories/   Spring Data JpaRepository interfaces
    ├── database/mappers/        entity ↔ domain model functions
    ├── message_queue/           RabbitMQ listeners + queue constants
    ├── messaging/               cross-module event listeners
    └── <external>/              storage / push_notification / security clients
```

**The layering laws:**
- Controllers (`api`) call services, map results to DTOs, and return. No queries, no
  business rules.
- Services (`service`) hold the logic and own transactions. They depend on repositories
  and infra adapters, never on controllers or DTOs.
- Domain (`domain`) is framework-free Kotlin: models, events, exceptions, IDs.
- Infra (`infra`) is where JPA, RabbitMQ, Redis, and HTTP clients live. Entities never
  leave this layer — they're mapped to domain models first.
- **Three representations of a thing:** JPA `Entity` (infra) ↔ domain `model` (domain) ↔
  `Dto` (api), connected by mapper functions. See [[domain-modeling]].

## Cross-module communication

Modules are decoupled two ways, both async, never a direct call:

1. **Domain events over RabbitMQ** — a module publishes a `ChirpEvent`; interested modules
   consume it from their own queue. E.g. `user` publishes `UserEvent.Created`; `notification`
   sends the verification email; `chat` creates a participant mirror. See [[rabbitmq-events]].
2. **Data mirroring** — instead of cross-module DB joins, a module keeps a local copy of
   the data it needs. `chat`'s `ChatParticipantEntity` mirrors the `user` identity, kept in
   sync via events. This is why each service also uses its **own Postgres schema**
   (`user_service`, `chat_service`, `notification_service`) — see [[jpa-persistence]].

In-process notification *within* a single module uses Spring's `ApplicationEventPublisher`
instead of RabbitMQ — see [[spring-application-events]].

## Adding a new feature module end-to-end

Follow this order; each step links the skill with the details.

1. **Create the module** — new dir + `build.gradle.kts` applying `chirp.spring-boot-service`,
   `kotlin("plugin.jpa")` if it has entities, `implementation(projects.common)`. Register it
   in root `settings.gradle.kts` and add `implementation(projects.<module>)` to
   `app/build.gradle.kts`. See [[gradle-build-system]].
2. **Model the domain** — domain `models` (data classes), `type`-safe IDs in `common` if new
   ones are needed, `domain/exception` types. See [[domain-modeling]].
3. **Persistence** — JPA entities under a new `<name>_service` schema, repositories, entity
   mappers. See [[jpa-persistence]].
4. **Service layer** — `@Service` with `@Transactional` business methods.
5. **REST surface** — controller under `/api/<area>`, request/response DTOs with validation,
   DTO mappers, a `@RestControllerAdvice` for the module's exceptions. See [[rest-api]].
6. **Events** — define `ChirpEvent` subtypes in `common`, publish via `EventPublisher`, add
   queues/bindings in `RabbitMqConfig`, write `@RabbitListener` consumers. See [[rabbitmq-events]].
7. **Cross-cutting** as needed: [[caching-redis]], [[websocket-realtime]], [[security-and-auth]],
   [[rate-limiting]], and integrations [[supabase-storage]] / [[firebase-push]] / [[mailgun-email]].
8. **Config + deploy** — add settings to `application.yml` + profiles, env vars. See
   [[configuration]] and [[deployment]].

## Conventions that apply everywhere

Language/framework idioms (DI style, KDoc, why entities aren't data classes, loggers) are
in [[kotlin-spring-conventions]]. Read that skill alongside this one — it's the global
ruleset; this skill is the structural map.

## Common mistakes

- **Feature module importing another feature module.** Breaks the dependency rule. Mirror
  the data and sync via events instead.
- **Business logic in a controller.** Controllers only orchestrate + map. Move it to a service.
- **Returning a JPA entity from a controller.** Map entity → domain model → DTO. Entities
  stay in `infra`.
- **New module under a different base package.** Its beans won't be component-scanned. Keep
  everything under `com.project.chirp`.
- **Synchronous cross-module call to "just get" some data.** There's no such path by design.
  Mirror it or consume an event.

## Source of truth

The **code** is authoritative. `STRUCTURE.md` at the repo root is a useful blueprint but has
drifted in places (e.g. it calls rate limiting "in-memory" — it is actually Redis + Lua).
When they disagree, follow the code and these skills.
