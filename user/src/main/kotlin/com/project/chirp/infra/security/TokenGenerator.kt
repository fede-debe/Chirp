package com.project.chirp.infra.security

import java.security.SecureRandom
import java.util.*

/***
 * Generates a secure token using SecureRandom and Base64 encoding.
 * @return A secure token as a Base64-encoded string.
 */
object TokenGenerator {
    fun generateSecureToken(): String {
        val bytes = ByteArray(32) { 0 }

        val secureRandom = SecureRandom()
        secureRandom.nextBytes(bytes)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }
}