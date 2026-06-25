package com.project.chirp.infra.social

import java.security.MessageDigest

/***
 * SHA-256 hex digest. Used to match a provider token's `nonce` claim against the client's
 * rawNonce (the client sends SHA256_hex(rawNonce) to the provider).
 */
object Sha256 {
    fun hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
