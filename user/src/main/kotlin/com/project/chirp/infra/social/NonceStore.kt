package com.project.chirp.infra.social

import com.project.chirp.domain.model.AuthProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/***
 * Single-use nonce guard backed by Redis, to prevent replay of a captured provider token.
 *
 * The hashed nonce (SHA256_hex(rawNonce), which is also the token's `nonce` claim) is recorded
 * atomically with SET NX. The first sign-in wins; a replay of the same token reuses the same
 * nonce and is rejected. The key is kept for the provider-token validity window. Redis is used
 * (not in-memory) so the guard holds across application instances — same rationale as the
 * rate limiters.
 */
@Component
class NonceStore(
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private const val NONCE_PREFIX = "social_nonce"
    }

    /***
     * Records the nonce as used. Returns true if it was newly recorded (first use), false if it
     * was already present (replay).
     */
    fun consume(provider: AuthProvider, nonceHash: String, ttl: Duration): Boolean {
        val key = "$NONCE_PREFIX:${provider.name.lowercase()}:$nonceHash"
        return redisTemplate.opsForValue().setIfAbsent(key, "1", ttl) == true
    }
}
