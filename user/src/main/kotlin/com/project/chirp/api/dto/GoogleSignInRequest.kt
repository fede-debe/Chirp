package com.project.chirp.api.dto

import jakarta.validation.constraints.NotBlank

/***
 * Body for POST /api/auth/google.
 * @param idToken The Google ID token (JWT) obtained by the client from Google Sign-In.
 * @param rawNonce The random string the client generated; the client passed SHA256_hex(rawNonce)
 *   to Google, so the token's `nonce` claim must equal SHA256_hex(rawNonce).
 */
data class GoogleSignInRequest(
    @field:NotBlank(message = "idToken is required")
    val idToken: String,
    @field:NotBlank(message = "rawNonce is required")
    val rawNonce: String,
)
