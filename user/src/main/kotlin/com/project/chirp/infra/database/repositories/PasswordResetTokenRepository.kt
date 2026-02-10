package com.project.chirp.infra.database.repositories

import com.project.chirp.infra.database.entities.PasswordResetTokenEntity
import com.project.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/***
 * Repository for managing password reset token entities.
 * @see findByToken: Finds a password reset token by its token value.
 * @see deleteByExpiresAtLessThan: Deletes password reset tokens that have expired.
 * @see invalidateActiveTokensForUser: Invalidates active password reset tokens for a specific user.
 *
 */
interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, Long> {
    fun findByToken(token: String): PasswordResetTokenEntity?
    fun deleteByExpiresAtLessThan(now: Instant)

    /** Using a custom query to invalidate active password reset tokens for a specific user. */
    @Modifying
    @Query(
        """
        UPDATE PasswordResetTokenEntity p
        SET p.usedAt = CURRENT_TIMESTAMP
        WHERE p.user = :user
    """
    )
    fun invalidateActiveTokensForUser(user: UserEntity)
}