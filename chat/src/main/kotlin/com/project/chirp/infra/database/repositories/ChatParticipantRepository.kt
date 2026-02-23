package com.project.chirp.infra.database.repositories

import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.ChatParticipantEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/***
 * Repository for managing chat participant entities.
 * @see findByUserIdIn: Finds chat participants by their user IDs (update all users in a chat)
 * @see findByEmailOrUsername: Finds a chat participant by their email or username.
 *
 */
interface ChatParticipantRepository : JpaRepository<ChatParticipantEntity, UserId> {
    fun findByUserIdIn(userIds: List<UserId>): Set<ChatParticipantEntity>

    // define query because deviate from the format of JpaRepository
    @Query(
        """
        SELECT p
        FROM ChatParticipantEntity p
        WHERE LOWER(p.username) = :query OR LOWER(p.email) = :query
    """
    )
    fun findByEmailOrUsername(query: String): ChatParticipantEntity?
}