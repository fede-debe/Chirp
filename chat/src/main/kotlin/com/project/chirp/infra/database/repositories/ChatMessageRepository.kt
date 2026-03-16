package com.project.chirp.infra.database.repositories

import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import com.project.chirp.infra.database.entities.ChatMessageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/***
 * Repository to fetch chat message entities.
 * @see findByChatIdBefore: Finds chat messages for a chat before a given time.
 * @see findLatestMessagesByChatIds: Finds the latest chat message for each chat ID.
 *
 * When it comes to pagination we need some page related parameters to decide which chunk of our messages table to load.
 * We are not using an Int values as page because it is not ideal with chat messages. The chat table could be frequently
 * changing if 2 users are currently chatting in that chat. Specific messages could be loaded multiple times if we use
 * pagination with Int values. We need to use a createdAt timestamp as a page parameter, we want to fetch X messages before
 * a certain time stamp because timestamps stay constant regardless of how many messages the client already sees.
 */
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, ChatMessageId> {

    /***
     * Finds chat messages for a chat before a given time.
     *
     * @param chatId: The unique identifier for the chat.
     * @param before: The timestamp before which chat messages should be fetched.
     * @param pageable: The pagination parameters. It tells our JPA repository the configuration about what kind of page you want to load with specific page size.
     * @return A slice of chat messages for the given chat before the given time.
     * If we return Page instead of Slice, we will get the total number of messages for the given chat before the given time.
     * Slice doesn't come with an extra query, but for infinite loading list we don't really care which page we are on.
     * We would use Page for an environment like Google browser where you can see the actual number of available pages and navigate through them.
     *
     * LEFT JOIN FETCH m.attachments eagerly loads attachments in the same query to avoid an N+1
     * problem when each message is later converted to its domain model (toChatMessage).
     *
     * ORDER BY m.createdAt DESC -> latest messages is on top of the list
     */
    @Query(
        """
        SELECT m
        FROM ChatMessageEntity m
        LEFT JOIN FETCH m.attachments
        WHERE m.chatId = :chatId
        AND m.createdAt < :before
        ORDER BY m.createdAt DESC
    """
    )
    fun findByChatIdBefore(
        chatId: ChatId,
        before: Instant,
        pageable: Pageable
    ): Slice<ChatMessageEntity>

    /***
     * Finds the latest chat message for each chat ID.
     * This is an Utility query used to fetch the latest message for each chat to display them within the chat list.
     *
     * @param chatIds: The unique identifiers for the chats.
     * @return A list of chat messages, one for each chat ID.
     *
     * LEFT JOIN FETCH m.attachments ensures that attachment data is fetched in the same query,
     * preventing an N+1 problem when chat list previews include attachment metadata.
     */
    @Query(
        """
        SELECT m
        FROM ChatMessageEntity m
        LEFT JOIN FETCH m.sender
        LEFT JOIN FETCH m.attachments
        WHERE m.chatId IN :chatIds
        AND (m.createdAt, m.id) = (
            SELECT m2.createdAt, m2.id
            FROM ChatMessageEntity m2
            WHERE m2.chatId = m.chatId
            ORDER BY m2.createdAt DESC 
            LIMIT 1
        )
    """
    )
    fun findLatestMessagesByChatIds(
        chatIds: Set<ChatId>
    ): List<ChatMessageEntity>
}