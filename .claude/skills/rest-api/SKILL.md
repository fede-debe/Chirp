---
name: rest-api
description: Use when adding or changing a REST endpoint, @RestController, request/response DTO, bean validation rule, custom validation annotation, the current-user lookup, or an exception-to-HTTP mapping (@RestControllerAdvice) in this Spring Boot backend.
---

# REST API Conventions

## Overview

Thin `@RestController`s under `/api/<area>` that validate input, call a service, and map the
result to a DTO. No business logic, no queries in controllers. Errors are thrown as domain
exceptions and translated to HTTP by a per-module `@RestControllerAdvice` that returns a
consistent `{ "code", "message" }` JSON body.

## Controller shape

```kotlin
@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService,
) {
    companion object { private const val DEFAULT_PAGE_SIZE = 20 }

    @GetMapping("/{chatId}/messages")
    fun getMessagesForChat(
        @PathVariable("chatId") chatId: ChatId,
        @RequestParam("before", required = false) before: Instant? = null,
        @RequestParam("pageSize", required = false) pageSize: Int = DEFAULT_PAGE_SIZE,
    ): List<ChatMessageDto> =
        chatService.getChatMessages(chatId, before, pageSize)

    @PostMapping
    fun createChat(@Valid @RequestBody body: CreateChatRequest): ChatDto =
        chatService.createChat(requestUserId, body.otherUserIds.toSet()).toChatDto()
}
```

Conventions:
- Class-level `@RequestMapping("/api/<area>")`; method-level `@GetMapping`/`@PostMapping`/
  `@DeleteMapping` with the sub-path.
- Return the **DTO** (or `List<DTO>`), produced by a mapper. Never return an entity. See
  [[domain-modeling]].
- Path variables typed as the type-safe ID (`ChatId`, `UserId`) — Spring converts the UUID.
- Optional query params use `required = false` + a default.
- Per-controller constants (default page size) in a `companion object`.

## Current authenticated user

Get the caller's id from the security context via the shared `requestUserId` extension
(`common/api/util/requestUserId.kt`) — don't read headers or principals manually in controllers:

```kotlin
import com.project.chirp.api.util.requestUserId
...
chatService.findChatsByUser(userId = requestUserId)
```

It returns `UserId` or throws `UnauthorizedException` if unauthenticated. It works because
`JwtAuthFilter` put the user id in the `SecurityContext` — see [[security-and-auth]].

## DTOs and validation

Request/response DTOs are `data class`es in `api/dto`. Validate requests with Jakarta Bean
Validation and trigger it with `@Valid` on the `@RequestBody`.

```kotlin
data class RegisterRequest(
    @field:Length(min = 3, max = 20, message = "Username must be between 3 and 20 characters long")
    @JsonProperty("username") val username: String,
    @field:Email(message = "Please provide a valid email address")
    @JsonProperty("email") val email: String,
    @field:Password
    @JsonProperty("password") val password: String,
)
```

- Use the **`@field:`** use-site target on validation annotations in Kotlin (`@field:Length`,
  `@field:Email`), or they won't apply to the backing field.
- `@JsonProperty` makes the wire name explicit.
- Validation requires `spring-boot-starter-validation` in the module (see [[gradle-build-system]]).

### Custom constraint annotation

Reusable rules become annotations (e.g. `@Password` composing `@Pattern`):

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
@Pattern(regexp = "^(?=.*[\\d!@#\$%^&*()_+...])(.{8,})\$", message = "Password must be at least 8 characters...")
annotation class Password(
    val message: String = "Password must be at least 8 characters and contain at least one digit or special character",
    val groups: Array<KClass<out Any>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
```

## Exception handling → HTTP

Services throw **domain exceptions** (see [[domain-modeling]]); they're never caught in the
controller. Each module has a `@RestControllerAdvice` mapping its exceptions to status codes,
returning a uniform body:

```kotlin
@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onUserAlreadyExists(e: UserAlreadyExistsException) = mapOf(
        "code" to "USER_EXISTS",
        "message" to e.message,
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onValidation(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.allErrors.map { it.defaultMessage ?: "Invalid value" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "VALIDATION_ERROR", "errors" to errors))
    }
}
```

The response contract is **`{ "code": "<MACHINE_CODE>", "message": "<human text>" }`** (extra
fields allowed, e.g. `verificationEmailResent`, or `errors` for validation). The `code` is a
stable SCREAMING_SNAKE string clients switch on; the `message` is human-readable.

- Module-specific exceptions → that module's advice (`AuthExceptionHandler`,
  `ChatExceptionHandler`, `NotificationExceptionHandler`).
- Cross-cutting exceptions (`UnauthorizedException`, `ForbiddenException`) are handled once in
  `common`'s `CommonExceptionHandler`. Don't re-handle them per module unless adding a status.
- Status conventions seen: `409 CONFLICT` (already exists / same password), `404 NOT_FOUND`,
  `401 UNAUTHORIZED` (bad credentials / invalid token), `403 FORBIDDEN` (not allowed / email
  unverified), `429 TOO_MANY_REQUESTS` (rate limit), `400 BAD_REQUEST` (validation).

For a genuinely ad-hoc not-found you may throw `ResponseStatusException(HttpStatus.NOT_FOUND)`
directly (used in a couple of read endpoints), but prefer a named domain exception.

## Endpoint naming

- Collection under the area root: `GET /api/chat`, `POST /api/chat`.
- Sub-resources/actions as path segments: `POST /api/chat/{chatId}/add`,
  `DELETE /api/chat/{chatId}/leave`, `GET /api/chat/{chatId}/messages`.
- Cursor pagination via query params (`?before=<instant>&pageSize=<n>`), not page numbers.

## Common mistakes

- Business logic or repository calls in a controller. Push into a service.
- Returning an entity instead of a DTO.
- Catching domain exceptions in the controller instead of letting the advice map them.
- Forgetting `@Valid`, or using `@Length`/`@Email` without the `@field:` target.
- Inventing a new error-body shape — keep `{ "code", "message" }`.
- Reading the JWT/principal by hand instead of `requestUserId`.
