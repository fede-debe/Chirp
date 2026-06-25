---
name: rate-limiting
description: Use when adding or changing request throttling in this backend — per-IP rate limiting on endpoints, per-email throttling for verification/reset emails, the @IpRateLimit annotation + interceptor, or the Redis Lua scripts that back them.
---

# Rate Limiting

## Overview

Two independent, **Redis-backed** rate limiters, both implemented with atomic **Lua scripts** so
counting is race-free across application instances:

- **IP rate limiting** — declarative, per-endpoint via an `@IpRateLimit` annotation + a
  `HandlerInterceptor`. Fixed window.
- **Email rate limiting** — programmatic, wraps email-sending actions, with **exponential
  backoff** per email address.

> Note: `STRUCTURE.md` calls these "in-memory" — that's stale. They are Redis + Lua. Follow the
> code.

## IP rate limiting (declarative)

Annotate an endpoint; an interceptor reads the annotation and enforces it.

```kotlin
@PostMapping("/login")
@IpRateLimit(requests = 10, duration = 1L, unit = TimeUnit.HOURS)
fun login(@RequestBody body: LoginRequest): AuthenticatedUserDto = ...
```

The annotation (`user/api/config/IpRateLimit.kt`):

```kotlin
annotation class IpRateLimit(
    val requests: Int = 60,
    val duration: Long = 1L,
    val unit: TimeUnit = TimeUnit.MINUTES,
)
```

The interceptor (`IpRateLimitInterceptor`, a `HandlerInterceptor` registered for `/api/**` by
`WebMvcConfig`) checks for the annotation on the handler method, resolves the client IP, runs the
limiter, and returns `429` when exceeded. It's globally toggled by config so dev can disable it:

```kotlin
@Component
class IpRateLimitInterceptor(
    private val ipRateLimiter: IpRateLimiter,
    private val ipResolver: IpResolver,
    @param:Value("\${chirp.rate-limit.ip.apply-limit}") private val applyLimit: Boolean,
) : HandlerInterceptor {
    override fun preHandle(request, response, handler): Boolean {
        if (handler is HandlerMethod && applyLimit) {
            val annotation = handler.getMethodAnnotation(IpRateLimit::class.java) ?: return true
            val clientIp = ipResolver.getClientIp(request)
            return try {
                ipRateLimiter.withIpRateLimit(clientIp, Duration.of(annotation.duration, annotation.unit.toChronoUnit()), annotation.requests) { true }
            } catch (e: RateLimitException) { response.sendError(429); false }
        }
        return true
    }
}
```

`apply-limit` is `false` in dev, `true` in prod (`chirp.rate-limit.ip.apply-limit`). See
[[configuration]].

### Client IP resolution (IpResolver)

Behind nginx, `request.remoteAddr` is the proxy, not the client. `IpResolver` only trusts the
`X-Real-IP` header when the request actually came from a **trusted proxy** (CIDR list in
`nginx.trusted-ips` config), validates/normalizes the IP, and in prod (`nginx.require-proxy:
true`) **rejects direct connections** that bypass the proxy. This stops clients from spoofing
their IP to dodge limits. Don't trust forwarded headers without the trusted-proxy check.

### IpRateLimiter (fixed window, Lua)

```kotlin
@Component
class IpRateLimiter(private val redisTemplate: StringRedisTemplate) {
    // loads classpath:ip_rate_limit.lua once (lazy), keyed "rate_limit:ip:<ip>"
    fun <T> withIpRateLimit(ipAddress: String, resetsIn: Duration, maxRequestsPerIp: Int, action: () -> T): T {
        val result = redisTemplate.execute(rateLimitScript, listOf(key), maxRequestsPerIp.toString(), resetsIn.seconds.toString())
        return if (result[0] <= maxRequestsPerIp) action() else throw RateLimitException(resetsInSeconds = result[1])
    }
}
```

The Lua script (`user/src/main/resources/ip_rate_limit.lua`) atomically: GET the counter; if
absent SET to 1 with TTL; else INCR if under the max; return `{count, ttl}`. Atomicity is the
reason for Lua — INCR+TTL in one round trip, no race.

## Email rate limiting (exponential backoff)

Wraps an email-sending action by email address. Used in `AuthController.resendVerification`:

```kotlin
emailRateLimiter.withRateLimit(email = body.email) {
    emailVerificationService.resendVerificationEmail(body.email)
}
```

```kotlin
@Component
class EmailRateLimiter(private val redisTemplate: StringRedisTemplate) {
    fun withRateLimit(email: String, action: () -> Unit) {
        val normalizedEmail = email.lowercase().trim()      // normalize so case/space can't bypass
        val result = redisTemplate.execute(rateLimitScript,
            listOf("rate_limit:email:$normalizedEmail", "email_attempt_count:$normalizedEmail"))
        if (result[0] == -1L) throw RateLimitException(resetsInSeconds = result[1])
        action()
    }
}
```

Backoff progression enforced in `email_rate_limit.lua`: 1st violation 60s, 2nd 300s, 3rd+ 3600s;
the attempt counter resets after 24h. Two Redis keys per email: the active lock and the attempt
counter. Always **normalize the email** (lowercase + trim) before keying.

## RateLimitException → 429

`RateLimitException(resetsInSeconds)` is thrown on limit; the IP interceptor sends `429`
directly, and `AuthExceptionHandler` maps it to `429 TOO_MANY_REQUESTS` with the standard
`{code, message}` body for the programmatic path. See [[rest-api]].

## Adding rate limiting

- To an endpoint, per IP: add `@IpRateLimit(requests, duration, unit)`. Done — the interceptor
  handles it (ensure the module has the interceptor/config; it currently lives in `user`).
- To an action, per identifier with backoff: wrap it in `emailRateLimiter.withRateLimit(key) { ... }`
  (or model a new limiter on it). Requires Redis (`spring-boot-starter-data-redis`).

## Common mistakes

- Trusting `X-Real-IP` / `remoteAddr` without the trusted-proxy check — spoofable. Use `IpResolver`.
- Forgetting to normalize the email key (case/whitespace bypass).
- Implementing the counter in Kotlin instead of the Lua script — reintroduces a race across
  instances. Keep the check-and-increment atomic in Redis.
- Leaving `apply-limit` on in dev and getting throttled during local testing.
