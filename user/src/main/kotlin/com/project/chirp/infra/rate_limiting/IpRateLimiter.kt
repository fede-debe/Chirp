package com.project.chirp.infra.rate_limiting

import com.project.chirp.domain.exception.RateLimitException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

/***
 * Rate limits requests based on the IP address.
 *
 * The rate limit script is defined in the `ip_rate_limit.lua` file.
 */
@Component
class IpRateLimiter(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val IP_RATE_LIMIT_PREFIX = "rate_limit:ip"
    }

    @Value("classpath:ip_rate_limit.lua")
    lateinit var rateLimitResource: Resource

    private val rateLimitScript by lazy {
        val script = rateLimitResource.inputStream.use {
            it.readBytes().decodeToString()
        }
        @Suppress("UNCHECKED_CAST")
        DefaultRedisScript(script, List::class.java as Class<List<Long>>)
    }

    /***
     * Executes an action with IP-based rate limiting.
     * @param ipAddress The IP address of the request.
     * @param resetsIn The duration after which the rate limit resets.
     * @param maxRequestsPerIp The maximum number of requests allowed per IP before rate limiting.
     * @param action The action to execute if not rate limited.
     * @return The result of the action.
     * @throws RateLimitException if the IP is rate limited.
     *
     * result The list returned by the Redis script, containing the current attempt count and TTL.
     */
    fun <T> withIpRateLimit(
        ipAddress: String,
        resetsIn: Duration,
        maxRequestsPerIp: Int,
        action: () -> T
    ): T {
        val key = "$IP_RATE_LIMIT_PREFIX:$ipAddress"

        val result = redisTemplate.execute(
            rateLimitScript,
            listOf(key),
            maxRequestsPerIp.toString(),
            resetsIn.seconds.toString()
        )

        val currentCount = result[0]

        return if (currentCount <= maxRequestsPerIp) {
            action()
        } else {
            val ttl = result[1]
            throw RateLimitException(resetsInSeconds = ttl)
        }
    }
}