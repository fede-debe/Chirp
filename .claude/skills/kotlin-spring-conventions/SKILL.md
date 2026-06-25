---
name: kotlin-spring-conventions
description: Use when writing or reviewing any Kotlin code in this Spring Boot backend — defining beans, services, configs, injecting dependencies or config values, writing KDoc, choosing data class vs class, or naming things. The global ruleset that applies across every module.
---

# Kotlin + Spring Boot Conventions

## Overview

Global Kotlin/Spring idioms used consistently across every module. These are the rules that
don't belong to one topic — read this alongside [[chirp-architecture]] before writing code
anywhere in the project.

Stack: Kotlin 2.2 (JVM 21), Spring Boot 4.0.2, Jackson 3 (`tools.jackson.*`, not the legacy
`com.fasterxml.jackson.*` — except a few existing DTO annotations).

## Dependency injection

**Always constructor injection. Never field injection (`@Autowired` on a property).**

```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) { ... }
```

Inject config values into the constructor with `@param:Value` (the `param:` use-site target
is required — the constructor parameter, not a property, receives the value):

```kotlin
@Service
class EmailService(
    private val javaMailSender: JavaMailSender,
    @param:Value("\${chirp.email.from}") private val emailFrom: String,
)
```

## Bean stereotypes — which annotation

| Annotation        | Use for                                                        |
|-------------------|---------------------------------------------------------------|
| `@Service`        | Business logic in the `service` layer.                        |
| `@Component`      | Infra adapters, filters, interceptors, listeners, helpers.   |
| `@Configuration`  | Bean-definition classes (`@Bean` factory methods).           |
| `@RestController` | HTTP controllers in `api/controllers`.                       |
| `@RestControllerAdvice` | Per-module exception handlers.                         |

`@Bean` factory methods live inside `@Configuration` classes (see `RedisConfig`,
`RabbitMqConfig`, `SupabaseRestClientConfig`).

## `class` vs `data class` (important)

- **Domain models, DTOs, events → `data class`.** They're immutable value carriers; you want
  `equals`/`hashCode`/`copy`.
- **JPA entities → plain `class` with `var` properties, never `data class`.** A data class's
  generated `equals`/`hashCode` conflicts with what Hibernate expects for managed entities
  and breaks proxies/lazy loading. This is a hard rule — see [[jpa-persistence]].

```kotlin
data class User(val id: UserId, val username: String, val email: String)   // domain model

class UserEntity(                                                           // JPA entity
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UserId? = null,
    @Column(nullable = false, unique = true) var email: String,
)
```

## KDoc style

The codebase is **heavily KDoc'd, and the docs explain *why*, not just what.** Match this.
Public classes and non-trivial functions get a KDoc block. Use `@param`/`@return`/`@throws`,
and use `@see` to point at related members. Document the reasoning behind non-obvious choices
(why `saveAndFlush`, why `EAGER` here, why a cache condition) inline.

```kotlin
/**
 * Sends a chat message, optionally with attachments.
 *
 * @CacheEvict evicts the cached message list for the chat so the next read is fresh.
 * Attachments are saved after saveAndFlush so the parent message ID exists for the FK.
 *
 * @param chatId The chat to post into.
 * @throws ChatNotFoundException if the chat doesn't exist for this sender.
 */
```

A KDoc that just restates the signature adds nothing — explain the decision or omit it.

## Logging

One logger per class via SLF4J, named from `javaClass`:

```kotlin
private val logger = LoggerFactory.getLogger(javaClass)
```

Log at boundaries and on failure paths (`logger.info` on success of an async action,
`logger.warn`/`logger.error` with the exception on failure). Infra adapters that can fail
silently-ish (event publish, email send, push) **catch, log, and continue** rather than
propagating — see `EventPublisher` and `EmailService`.

## Input handling

- **Trim user-supplied strings** before persisting/comparing: `email.trim()`,
  `username.trim()`, `content.trim()`. Normalize emails to lowercase where used as a key
  (see [[rate-limiting]]).
- Prefer Kotlin null-safety to defensive `if` chains: `?:`, `?.let`, `findByIdOrNull`.

## Constants

Per-class constants go in a `companion object`. Shared routing/queue/event names go in a
dedicated `object` (e.g. `UserEventConstants`, `MessageQueues`) — see [[rabbitmq-events]].

```kotlin
companion object {
    private const val PING_INTERVAL_MS = 30_000L
}
```

## Naming

- Skills/files/classes describe what they are: `JwtAuthFilter`, `IpRateLimiter`,
  `SupabaseStorageService`, `MessageCacheEvictionHelper`.
- Mapper functions are extension functions named `toX`: `UserEntity.toUser()`,
  `User.toUserDto()`, `Chat.toChatDto()`. See [[domain-modeling]].
- Type-safe IDs are `typealias`es ending in `Id`: `UserId`, `ChatId`, `ChatMessageId`.

## Compiler settings (already configured, don't fight them)

`chirp.kotlin-common` sets `-Xjsr305=strict` (treat JSR-305 nullability as strict) and
`-Xannotation-default-target=param-property`. JVM target/toolchain is 21. Write code that
respects strict nullability — don't sprinkle `!!` to silence it except where an ID is
guaranteed non-null after a save (the codebase does use `id!!` in entity→model mappers).

## Common mistakes

- Using `data class` for a JPA entity. → plain `class` with `var`.
- `@Value` without `@param:` target in a constructor. → `@param:Value("\${...}")`.
- Importing `com.fasterxml.jackson.*` for new Jackson code. → use `tools.jackson.*` (Jackson 3).
- Letting a service propagate an infra failure that should be swallowed-and-logged (or vice
  versa). Match the existing adapter's policy.
- Anemic KDoc that repeats the signature. Document the *why* or skip it.
