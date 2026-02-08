package com.project.chirp.infra.database.repositories

import com.project.chirp.infra.database.entities.EmailVerificationTokenEntity
import com.project.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/***
 * Repository for managing email verification token entities.
 * @see findByToken: Finds an email verification token by its token value.
 * @see deleteByExpiresAtLessThan: Deletes email verification tokens that have expired.
 * @see findByUserAndUsedAtIsNull: Finds email verification tokens for a specific user that have not been used.
 *
 */
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationTokenEntity, Long> {
    fun findByToken(token: String): EmailVerificationTokenEntity?
    fun deleteByExpiresAtLessThan(now: Instant)
    fun findByUserAndUsedAtIsNull(user: UserEntity): List<EmailVerificationTokenEntity>
}