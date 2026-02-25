package com.project.chirp.infra.database.repositories

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.ChatEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/***
 * Repository to fetch chat entities.
 * @see findChatById: Finds a chat by its ID and checks if the user is a participant.
 * @see findAllByUserId: Finds all chats where the user is a participant.
 *
 */
interface ChatRepository : JpaRepository<ChatEntity, ChatId> {
    /***
     * Finds a chat by its ID and checks if the user is a participant.
     * This prevents fetching chats where the user is not a participant.
     * Race condition: if user is removed from the chat before the query is executed,
     * the chat will not be fetched.
     *
     * LEFT JOIN FETCH is used to eagerly load the participants and creator entities.
     * Regardless we find participants that belong to this chat, we still keep the
     * entries (SELECT c, FROM ChatEntity c), an inner join would only return results
     * where the are rows that match in both tables. With inner table we would only get
     * chats where there are actual participants being part of chat. If specific
     * chat has no participants, then with a normal joint that chat would not be
     * contained in the end result.
     * With LEFT JOIN FETCH we will always get the chat, even if it has no participants
     * or creator. We will always make sure that the previous chat we try to join will
     * stay there in the search results to be processed further.
     *
     * Lazy fetching of participants and creator is avoided by using LEFT JOIN FETCH
     * and it will be not done here. When we will fetch multiple chats, we will avoid
     * the N+1 problem by using LEFT JOIN FETCH.
     *
     * With the subquery we ensure that the chat is not fetched if the user is not a participant.
     * A user must be part of the participants table for the chat to be fetched.
     */
    @Query(
        """
        SELECT c
        FROM ChatEntity c
        LEFT JOIN FETCH c.participants
        LEFT JOIN FETCH c.creator
        WHERE c.id = :id
        AND EXISTS (
            SELECT 1
            FROM c.participants p
            WHERE p.userId = :userId
        )
    """
    )
    fun findChatById(id: ChatId, userId: UserId): ChatEntity?

    /***
     * Finds all chats where the user is a participant.
     * This query is very similar tp the one used in findChatById, but here
     * we don't need to check if the user is a participant because we are
     * already sure that the user is a participant.
     *
     * By not using lazy with participants and creator we avoid the N+1 problem
     * when fetching multiple chats. By using LEFT JOIN FETCH we already eagerly
     * load the participants and creator entities.
     */
    @Query(
        """
        SELECT c
        FROM ChatEntity c
        LEFT JOIN FETCH c.participants
        LEFT JOIN FETCH c.creator
        WHERE EXISTS (
            SELECT 1
            FROM c.participants p
            WHERE p.userId = :userId
        )
    """
    )
    fun findAllByUserId(userId: UserId): List<ChatEntity>
}