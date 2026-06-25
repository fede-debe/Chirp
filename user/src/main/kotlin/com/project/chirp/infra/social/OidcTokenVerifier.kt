package com.project.chirp.infra.social

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.project.chirp.domain.exception.InvalidTokenException
import java.net.URI

/***
 * Verifies an OpenID Connect ID token (RS256) against a provider's remote JWKS.
 *
 * Enforces, server-side only (the client token is never trusted on its face):
 * - signature against the provider's published JWKS (fetched + cached by Nimbus),
 * - `exp` not passed (and `nbf`/`iat` sanity) via the claims verifier,
 * - `aud` is one of the configured allowed audiences (client ids / bundle id),
 * - `iss` is one of the allowed issuers.
 *
 * Provider-specific claim extraction (email, nonce, picture, email_verified) is done by the
 * Google/Apple verifiers that wrap this. Any verification failure surfaces as
 * [InvalidTokenException] so it maps to the same 401 as other invalid-token cases.
 */
class OidcTokenVerifier(
    jwksUrl: String,
    private val allowedIssuers: Set<String>,
    allowedAudiences: Set<String>,
) {
    init {
        // Fail loudly rather than silently accepting any audience due to missing config.
        check(allowedAudiences.isNotEmpty()) {
            "No allowed audiences (client ids) configured for OIDC verification"
        }
    }

    private val processor: DefaultJWTProcessor<SecurityContext> =
        DefaultJWTProcessor<SecurityContext>().apply {
            val jwkSource: JWKSource<SecurityContext> = JWKSourceBuilder
                .create<SecurityContext>(URI.create(jwksUrl).toURL())
                .retrying(true)
                .build()
            jwsKeySelector = JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, jwkSource)
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier<SecurityContext>(
                allowedAudiences,
                JWTClaimsSet.Builder().build(),
                setOf("sub", "iat", "exp"),
                emptySet(),
            )
        }

    fun verify(token: String): JWTClaimsSet {
        val claims = try {
            processor.process(token, null)
        } catch (e: Exception) {
            throw InvalidTokenException("Invalid social identity token")
        }
        if (claims.issuer !in allowedIssuers) {
            throw InvalidTokenException("Invalid token issuer")
        }
        return claims
    }
}
