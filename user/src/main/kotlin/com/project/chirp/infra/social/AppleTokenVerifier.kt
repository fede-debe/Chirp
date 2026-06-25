package com.project.chirp.infra.social

import com.project.chirp.domain.model.AuthProvider
import com.project.chirp.domain.model.VerifiedSocialIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/***
 * Verifies Apple identity tokens and extracts the identity.
 *
 * Audience allowlist (`aud`) is the iOS app bundle id(s) from config
 * (`chirp.social.apple.client-ids`). Apple sign-in is iOS-only (native), so no Service ID is
 * needed. Apple returns the name only on first authorization, so `fullName` is taken from the
 * request body (not the token). The email may be an Apple private-relay address — it's accepted
 * and stored as-is. Built lazily so the app boots without Apple config.
 */
@Component
class AppleTokenVerifier(
    @param:Value("\${chirp.social.apple.client-ids}") private val clientIds: List<String>,
) {
    companion object {
        private const val JWKS_URL = "https://appleid.apple.com/auth/keys"
        private const val ISSUER = "https://appleid.apple.com"
    }

    private val verifier by lazy {
        OidcTokenVerifier(
            jwksUrl = JWKS_URL,
            allowedIssuers = setOf(ISSUER),
            allowedAudiences = clientIds.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
        )
    }

    fun verify(identityToken: String, fullName: String?): VerifiedSocialIdentity {
        val claims = verifier.verify(identityToken)
        return VerifiedSocialIdentity(
            provider = AuthProvider.APPLE,
            providerId = claims.subject,
            email = claims.getStringClaim("email"),
            emailVerified = claims.readEmailVerified(),
            name = fullName,
            pictureUrl = null,
            nonce = claims.getStringClaim("nonce"),
        )
    }
}
