package com.project.chirp.service

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.domain.exception.InvalidTokenException
import com.project.chirp.domain.model.AuthenticatedUser
import com.project.chirp.domain.model.VerifiedSocialIdentity
import com.project.chirp.infra.database.entities.UserEntity
import com.project.chirp.infra.database.mappers.toUser
import com.project.chirp.infra.database.repositories.RefreshTokenRepository
import com.project.chirp.infra.database.repositories.UserRepository
import com.project.chirp.infra.message_queue.EventPublisher
import com.project.chirp.infra.social.AppleTokenVerifier
import com.project.chirp.infra.social.GoogleTokenVerifier
import com.project.chirp.infra.social.NonceStore
import com.project.chirp.infra.social.Sha256
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/***
 * Google / Apple social sign-in. Verifies the provider ID token (signature, issuer, audience,
 * expiry — see the verifiers), enforces email_verified + single-use nonce, resolves the account
 * (lookup → link → take over → create), and issues THIS backend's own tokens via the same
 * [AuthTokenService] used by email/password login. The provider token only establishes identity;
 * it is never stored or returned.
 *
 * @see signInWithGoogle
 * @see signInWithApple
 */
@Service
class SocialAuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authTokenService: AuthTokenService,
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val appleTokenVerifier: AppleTokenVerifier,
    private val nonceStore: NonceStore,
    private val eventPublisher: EventPublisher,
) {
    companion object {
        /** Keep the nonce reserved for at least the provider-token validity window. */
        private val NONCE_TTL: Duration = Duration.ofHours(1)
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun signInWithGoogle(idToken: String, rawNonce: String): AuthenticatedUser {
        val identity = googleTokenVerifier.verify(idToken)
        verifyNonce(identity, rawNonce)
        return resolveAndIssue(identity)
    }

    @Transactional
    fun signInWithApple(identityToken: String, rawNonce: String, fullName: String?): AuthenticatedUser {
        val identity = appleTokenVerifier.verify(identityToken, fullName)
        verifyNonce(identity, rawNonce)
        return resolveAndIssue(identity)
    }

    /***
     * The token's `nonce` must equal SHA256_hex(rawNonce), and the nonce must not have been used
     * before (replay protection). Rejects with [InvalidTokenException] (→ 401) on either failure.
     */
    private fun verifyNonce(identity: VerifiedSocialIdentity, rawNonce: String) {
        val expected = Sha256.hex(rawNonce)
        if (identity.nonce == null || identity.nonce != expected) {
            throw InvalidTokenException("Invalid or missing nonce")
        }
        if (!nonceStore.consume(identity.provider, expected, NONCE_TTL)) {
            throw InvalidTokenException("Nonce has already been used")
        }
    }

    /***
     * Resolves the account for a verified social identity, provisions the chat-module participant
     * mirror once, and issues this backend's tokens.
     *
     * The mirror is created by the chat module when it receives [UserEvent.Verified]. We publish
     * that event exactly once per user — the first social sign-in — gated by
     * [UserEntity.chatParticipantProvisioned], mirroring how email verification provisions the
     * mirror a single time. This both creates the mirror for brand-new social users and back-fills
     * any user whose flag is still false (e.g. linked to an already-verified account, or a returning
     * login from before this flag existed); on every later login the flag is set, so nothing is
     * published. Applies to both Google and Apple, which share this path. Without it, a social user
     * with no mirror can authenticate but cannot be added to / create chats.
     */
    private fun resolveAndIssue(identity: VerifiedSocialIdentity): AuthenticatedUser {
        val user = resolveAccount(identity)
        if (!user.chatParticipantProvisioned) {
            publishVerified(user)
            user.chatParticipantProvisioned = true
            userRepository.save(user)
        }
        return authTokenService.issueTokens(user.toUser())
    }

    /***
     * Account resolution (security-critical):
     * 1. Found by (provider, providerId) → existing social account.
     * 2. Else by verified email:
     *    - existing & email already verified → link the provider.
     *    - existing & email NOT verified → account takeover: mark verified, drop the password,
     *      link provider, invalidate existing sessions. This stops anyone who pre-registered the
     *      email with a chosen password from retaining access, now that the provider has proven
     *      ownership of the email.
     * 3. No account → create a password-less, already-verified account with a generated username.
     *
     * Returns the resolved [UserEntity]; the caller publishes UserEvent.Verified for it.
     */
    private fun resolveAccount(identity: VerifiedSocialIdentity): UserEntity {
        userRepository.findByAuthProviderAndProviderId(identity.provider, identity.providerId)
            ?.let { return it }

        // Beyond this point we link or create by email, so the provider MUST give a verified email.
        val email = identity.email?.trim()
        if (email.isNullOrBlank() || !identity.emailVerified) {
            throw InvalidTokenException("Provider did not supply a verified email")
        }

        val existing = userRepository.findByEmail(email)
        if (existing != null) {
            return if (existing.hasVerifiedEmail) {
                linkProvider(existing, identity)
            } else {
                takeOverAccount(existing, identity)
            }
        }

        return createAccount(identity, email)
    }

    private fun linkProvider(user: UserEntity, identity: VerifiedSocialIdentity): UserEntity {
        user.authProvider = identity.provider
        user.providerId = identity.providerId
        val saved = userRepository.save(user)
        logger.info("Linked ${identity.provider} to existing verified account ${saved.id}")
        return saved
    }

    private fun takeOverAccount(user: UserEntity, identity: VerifiedSocialIdentity): UserEntity {
        user.hasVerifiedEmail = true
        user.hashedPassword = null
        user.authProvider = identity.provider
        user.providerId = identity.providerId
        val saved = userRepository.save(user)
        // Invalidate any sessions issued before takeover.
        refreshTokenRepository.deleteByUserId(saved.id!!)
        logger.info("Took over unverified account ${saved.id} via ${identity.provider}")
        return saved
    }

    private fun createAccount(identity: VerifiedSocialIdentity, email: String): UserEntity {
        val saved = userRepository.saveAndFlush(
            UserEntity(
                email = email,
                username = generateUniqueUsername(identity.name, email),
                hashedPassword = null,
                authProvider = identity.provider,
                providerId = identity.providerId,
                hasVerifiedEmail = true,
            )
        )
        logger.info("Created social account ${saved.id} via ${identity.provider}")
        return saved
    }

    private fun publishVerified(user: UserEntity) {
        eventPublisher.publish(
            UserEvent.Verified(
                userId = user.id!!,
                email = user.email,
                username = user.username,
            )
        )
    }

    /***
     * Derives a unique username from the provider name (or the email local-part), sanitized to
     * lowercase alphanumerics, appending digits to resolve collisions.
     */
    private fun generateUniqueUsername(name: String?, email: String): String {
        val base = sanitize(name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"))
            .ifBlank { "user" }
            .take(20)

        if (!userRepository.existsByUsername(base)) return base

        var suffix = 1
        while (true) {
            val suffixStr = suffix.toString()
            val candidate = base.take(20 - suffixStr.length) + suffixStr
            if (!userRepository.existsByUsername(candidate)) return candidate
            suffix++
        }
    }

    private fun sanitize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")
}
