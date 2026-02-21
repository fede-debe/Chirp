package com.project.chirp.infra.database.repositories

import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository

/***
 * Repository for managing refresh tokens in the database.
 * @see findByUserIdAndHashedToken: Finds a refresh token by user ID and hashed token.
 * @see deleteByUserIdAndHashedToken: Deletes a refresh token by user ID and hashed token.
 * @see deleteByUserId: Deletes all refresh tokens for a given user ID. This can happen when
 * a user logs out or their session expires or the password is changed.
 */
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByUserIdAndHashedToken(userId: UserId, hashedToken: String): RefreshTokenEntity?
    fun deleteByUserIdAndHashedToken(userId: UserId, hashedToken: String)
    fun deleteByUserId(userId: UserId)
}