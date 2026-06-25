package com.project.chirp.api.dto

import jakarta.validation.constraints.NotBlank

/***
 * Body for POST /api/auth/apple.
 * @param identityToken The Apple identity token (JWT) from Sign in with Apple.
 * @param rawNonce The random string the client generated; the token's `nonce` claim must equal SHA256_hex(rawNonce).
 * @param fullName Present only on the FIRST authorization (Apple returns the name once). Used for
 *   username creation on initial sign-up; absent on later logins.
 */
data class AppleSignInRequest(
    @field:NotBlank(message = "identityToken is required")
    val identityToken: String,
    @field:NotBlank(message = "rawNonce is required")
    val rawNonce: String,
    val fullName: String? = null,
)
