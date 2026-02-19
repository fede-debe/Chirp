package com.project.chirp.infra.rate_limiting

import com.project.chirp.domain.exception.RateLimitException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * Handles rate limiting for email-related operations using Redis-backed exponential backoff.
 *
 * This component implements a progressive rate limiting strategy to prevent abuse of email
 * functionality (e.g., password reset, verification emails). It uses a Lua script executed
 * atomically in Redis to ensure thread-safe rate limiting across distributed instances.
 *
 * ## Rate Limiting Strategy
 *
 * The rate limiter uses **exponential backoff** with the following progression:
 * - **1st violation**: 60 seconds (1 minute) cooldown
 * - **2nd violation**: 300 seconds (5 minutes) cooldown
 * - **3rd+ violations**: 3600 seconds (1 hour) cooldown
 *
 * The attempt counter resets after 24 hours of no violations, allowing the backoff
 * progression to start fresh.
 *
 * ## How It Works
 *
 * 1. **Check Active Rate Limit**: If a rate limit key exists for the email, the request
 *    is immediately rejected with the remaining TTL.
 *
 * 2. **Track Attempts**: An attempt counter tracks how many times the user has been
 *    rate limited (used to determine backoff duration).
 *
 * 3. **Apply Backoff**: When the action is executed, a rate limit key is set with an
 *    expiration based on the current attempt count.
 *
 * 4. **Execute Action**: If not rate limited, the provided action is executed.
 *
 * ## Redis Keys
 *
 * - `rate_limit:email:{normalized_email}`: Active rate limit lock (exists during cooldown)
 * - `email_attempt_count:{normalized_email}`: Counter for backoff progression (24h TTL)
 *
 * ## Thread Safety
 *
 * The Lua script (`email_rate_limit.lua`) executes atomically in Redis, ensuring that
 * concurrent requests from multiple application instances are handled correctly without
 * race conditions.
 *
 * @property redisTemplate Redis template for executing the rate limit Lua script
 * @throws RateLimitException when the rate limit has been exceeded, includes seconds until reset
 *
 * @see withRateLimit Main function to wrap email operations with rate limiting
 */
@Component
class EmailRateLimiter(
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
        private const val EMAIL_RATE_LIMIT_PREFIX = "rate_limit:email"
        private const val EMAIL_ATTEMPT_COUNT_PREFIX = "email_attempt_count"
    }

    @Value("classpath:email_rate_limit.lua")
    lateinit var rateLimitResource: Resource

    private val rateLimitScript by lazy {
        val script = rateLimitResource.inputStream.use {
            it.readBytes().decodeToString()
        }
        @Suppress("UNCHECKED_CAST")
        DefaultRedisScript(script, List::class.java as Class<List<Long>>)
    }

    /**
     * Executes the provided action with rate limiting applied for the given email address.
     *
     * The email is normalized (lowercased and trimmed) before checking rate limits to prevent
     * bypassing via case variations or whitespace.
     *
     * @param email The email address to apply rate limiting for
     * @param action The operation to execute if not rate limited (e.g., sending an email)
     * @throws RateLimitException if the email is currently rate limited, with `resetsInSeconds`
     *         indicating how long until the rate limit expires
     *
     * @sample
     * ```
     * emailRateLimiter.withRateLimit("user@example.com") {
     *     emailService.sendPasswordResetEmail(user)
     * }
     * ```
     */
    fun withRateLimit(
        email: String,
        action: () -> Unit
    ) {
        val normalizedEmail = email.lowercase().trim()

        val rateLimitKey = "$EMAIL_RATE_LIMIT_PREFIX:$normalizedEmail"
        val attemptCountKey = "$EMAIL_ATTEMPT_COUNT_PREFIX:$normalizedEmail"

        /**
         * Executes the Lua script to check and update rate limit counters.
         * Returns a list with two elements: attempt count and TTL (time to live).
         */
        val result = redisTemplate.execute(
            rateLimitScript,
            listOf(rateLimitKey, attemptCountKey)
        )

        val attemptCount = result[0]
        val ttl = result[1]

        if (attemptCount == -1L) {
            throw RateLimitException(resetsInSeconds = ttl)
        }

        action()
    }
}