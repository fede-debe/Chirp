package com.project.chirp.infra.social

import com.project.chirp.domain.model.AuthProvider
import com.project.chirp.domain.model.VerifiedSocialIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/***
 * Verifies Google ID tokens and extracts the identity.
 *
 * Audience allowlist (`aud`) comes from config (`chirp.social.google.client-ids`) — there are
 * several: Android, iOS, and the Web/server client id. JWKS URL and issuers are protocol
 * constants. The verifier is built lazily so the app still boots when client ids are unset
 * (only the /api/auth/google endpoint will then fail).
 */
@Component
class GoogleTokenVerifier(
    @param:Value("\${chirp.social.google.client-ids}") private val clientIds: List<String>,
) {
    companion object {
        private const val JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
        private val ISSUERS = setOf("https://accounts.google.com", "accounts.google.com")
    }

    private val verifier by lazy {
        OidcTokenVerifier(
            jwksUrl = JWKS_URL,
            allowedIssuers = ISSUERS,
            allowedAudiences = clientIds.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
        )
    }

    fun verify(idToken: String): VerifiedSocialIdentity {
        val claims = verifier.verify(idToken)
        return VerifiedSocialIdentity(
            provider = AuthProvider.GOOGLE,
            providerId = claims.subject,
            email = claims.getStringClaim("email"),
            emailVerified = claims.readEmailVerified(),
            name = claims.getStringClaim("name"),
            pictureUrl = claims.getStringClaim("picture"),
            nonce = claims.getStringClaim("nonce"),
        )
    }
}
