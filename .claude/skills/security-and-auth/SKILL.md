---
name: security-and-auth
description: Use when working on authentication or authorization in this backend — JWT access/refresh tokens, the Spring Security filter chain, the JWT auth filter, password hashing, login/refresh/logout, or the email-verification and password-reset token flows.
---

# Security & Authentication

## Overview

Stateless **JWT** authentication. A short-lived access token authorizes each request; a
long-lived, DB-stored, hashed **refresh token** rotates them. Spring Security runs sessionless
with a custom filter that puts the user id into the `SecurityContext`, where the shared
`requestUserId` reads it (see [[rest-api]]). The `JwtService` lives in `common` so any module can
validate a token without depending on `user`.

## JwtService (common)

Signs/validates HS256 tokens. The secret is base64-decoded from config; access TTL is
configurable, refresh is a fixed 30 days.

```kotlin
@Service
class JwtService(
    @param:Value("\${jwt.secret}") private val secretBase64: String,
    @param:Value("\${jwt.expiration-minutes}") private val expirationMinutes: Int,
) {
    private val secretKey = Keys.hmacShaKeyFor(Base64.Default.decode(secretBase64))
    val refreshTokenValidityMs = 30 * 24 * 60 * 60 * 1000L

    fun generateAccessToken(userId: UserId): String   // claim type="access", subject=userId
    fun generateRefreshToken(userId: UserId): String  // claim type="refresh"
    fun validateAccessToken(token: String): Boolean   // signature valid AND type=="access"
    fun getUserIdFromToken(token: String): UserId      // throws InvalidTokenException if unparseable
}
```

Key points:
- Token `subject` = `userId`; a custom `type` claim (`"access"` / `"refresh"`) distinguishes
  them so a refresh token can't be used as an access token and vice versa.
- `parseAllClaims` strips a leading `Bearer ` and returns `null` on any parse/verify failure
  (validation never throws for an invalid token; it returns false/null).
- Config: `jwt.secret` (base64, from `JWT_SECRET_BASE64` env) and `jwt.expiration-minutes`
  (1000 in dev, 15 in prod). See [[configuration]].

## Spring Security config (app module)

`SecurityConfig` defines the filter chain: stateless, CSRF off, JWT filter inserted, 401 on
unauthenticated.

```kotlin
@Bean
fun filterChain(http: HttpSecurity, jwtAuthFilter: JwtAuthFilter): SecurityFilterChain =
    http.csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/auth/change-password").authenticated()
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
        .build()
```

- **Stateless** — no `HttpSession`. CSRF disabled because there's no cookie/session.
- Public: everything under `/api/auth/**` (register/login/refresh/verify/forgot/reset). Note
  `change-password` is under `/api/auth` but requires auth (it's matched as authenticated).
- Everything else requires authentication; unauthenticated → `401` (no redirect).

## JwtAuthFilter (user module)

A `OncePerRequestFilter` placed before `UsernamePasswordAuthenticationFilter`. It reads the
`Authorization: Bearer` header, validates the access token, and stores the user id as the
authentication principal:

```kotlin
@Component
class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {
    override fun doFilterInternal(request, response, filterChain) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authHeader != null && authHeader.startsWith("Bearer ") && jwtService.validateAccessToken(authHeader)) {
            val userId = jwtService.getUserIdFromToken(authHeader)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, emptyList())
        }
        filterChain.doFilter(request, response)
    }
}
```

The principal is the `UserId` itself — that's what `requestUserId` casts and returns.

## Passwords

`PasswordEncoder` wraps Spring Security's `BCryptPasswordEncoder`. Store only the bcrypt hash;
compare with `matches`. Validate password strength at the API edge with the `@Password`
constraint (see [[rest-api]]).

```kotlin
@Component
class PasswordEncoder {
    private val bcrypt = BCryptPasswordEncoder()
    fun encode(rawPassword: String): String? = bcrypt.encode(rawPassword)
    fun matches(rawPassword: String, encodedPassword: String): Boolean = bcrypt.matches(rawPassword, encodedPassword)
}
```

## Refresh token rotation (AuthService)

Refresh tokens are **SHA-256 hashed before storage** (never stored raw) and **rotated** on every
refresh:

```kotlin
@Transactional
fun refresh(refreshToken: String): AuthenticatedUser {
    if (!jwtService.validateRefreshToken(refreshToken)) throw InvalidTokenException("Invalid refresh token")
    val userId = jwtService.getUserIdFromToken(refreshToken)
    val hashed = hashToken(refreshToken)                                  // SHA-256, Base64
    refreshTokenRepository.findByUserIdAndHashedToken(userId, hashed) ?: throw InvalidTokenException(...)
    refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashed)   // single-use: delete old
    val newAccess = jwtService.generateAccessToken(userId)
    val newRefresh = jwtService.generateRefreshToken(userId)
    storeRefreshToken(userId, newRefresh)                                 // store new (hashed) with expiry
    return AuthenticatedUser(user.toUser(), newAccess, newRefresh)
}
```

- Login issues access + refresh, stores the hashed refresh with `expiresAt`.
- Refresh validates the JWT, confirms the hash exists in the DB, deletes it, issues + stores a
  new pair (rotation).
- Logout deletes the stored hash for that refresh token.
- The raw refresh token is never persisted; only `SHA-256(token)` Base64 is.

## Token-based flows (verification & reset)

Email verification and password reset use the same one-time-token pattern (separate tables with
`token`, `userId`, `expiresAt`, `usedAt`): issue a token, deliver it via a `UserEvent` →
`notification` email (see [[rabbitmq-events]], [[mailgun-email]]), then validate it on the
verify/reset endpoint and **mark used rather than delete**. Token generation is in
`user/.../infra/security/TokenGenerator.kt`.

## Common mistakes

- Reading the principal/headers manually instead of `requestUserId`.
- Storing a raw refresh token, or not rotating it on refresh.
- Putting `JwtService` in a feature module — it's shared, in `common`.
- Adding a stateful/session-based mechanism — the chain is intentionally stateless.
- Treating rate limiting as part of this skill — it's separate; see [[rate-limiting]].
