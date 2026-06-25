package com.project.chirp.domain.model

/***
 * The trusted identity extracted from a provider ID token AFTER its signature, issuer, audience
 * and expiry have been verified server-side. Business rules (email_verified, nonce, account
 * linking) are applied on top of this — see SocialAuthService.
 *
 * @param provider Which provider issued the token (GOOGLE / APPLE).
 * @param providerId The provider's stable user id (`sub`). The durable link key — survives email changes.
 * @param email The verified email, if the token carried one (may be absent for returning users).
 * @param emailVerified Whether the provider asserts the email is verified (Boolean true or string "true").
 * @param name Display name if present (Google `name`, or Apple's body `fullName`). Used for username creation.
 * @param pictureUrl Profile picture URL if present (Google `picture`).
 * @param nonce The token's `nonce` claim (provider received SHA256_hex(rawNonce)); validated against the request.
 */
data class VerifiedSocialIdentity(
    val provider: AuthProvider,
    val providerId: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
    val pictureUrl: String?,
    val nonce: String?,
)
