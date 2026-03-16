package com.project.chirp.infra.database.repositories

import com.project.chirp.domain.type.ChatMessageAttachmentId
import com.project.chirp.infra.database.entities.ChatMessageAttachmentEntity
import org.springframework.data.jpa.repository.JpaRepository

/***
 * Repository for managing chat message attachment entities.
 *
 * No custom queries are needed here because attachments are always loaded as part of their parent
 * message via LEFT JOIN FETCH in ChatMessageRepository. Persistence (saveAll) and cascade deletes
 * are the only operations performed directly against this repository.
 */
interface ChatMessageAttachmentRepository : JpaRepository<ChatMessageAttachmentEntity, ChatMessageAttachmentId>