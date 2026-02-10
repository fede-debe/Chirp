package com.project.chirp.infra.database.repositories

import com.project.chirp.infra.database.entities.EmailVerificationTokenEntity
import com.project.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/***
 * Repository for managing email verification token entities.
 * @see findByToken: Finds an email verification token by its token value.
 * @see deleteByExpiresAtLessThan: Deletes email verification tokens that have expired.
 * @see invalidateActiveTokensForUser: Invalidates active email verification tokens for a specific user.
 *
 */
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationTokenEntity, Long> {
    fun findByToken(token: String): EmailVerificationTokenEntity?
    fun deleteByExpiresAtLessThan(now: Instant)

    @Modifying
    @Query(
        """
        UPDATE EmailVerificationTokenEntity e
        SET e.usedAt = CURRENT_TIMESTAMP 
        WHERE e.user = :user
    """
    )
    fun invalidateActiveTokensForUser(user: UserEntity)
}