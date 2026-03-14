package com.project.chirp.infra.database

import com.project.chirp.domain.type.UserId
import org.springframework.data.jpa.repository.JpaRepository

/***
 * Repository for managing device tokens in the database.
 * @see findByUserIdIn: Finds device tokens for a list of user IDs.
 * @see findByToken: Finds a device token by its token value.
 * @see deleteByToken: Deletes a device token by its token value.
 */
interface DeviceTokenRepository : JpaRepository<DeviceTokenEntity, Long> {
    fun findByUserIdIn(userIds: List<UserId>): List<DeviceTokenEntity>
    fun findByToken(token: String): DeviceTokenEntity?
    fun deleteByToken(token: String)
}