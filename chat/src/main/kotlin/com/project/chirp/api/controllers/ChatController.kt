package com.project.chirp.api.controllers

import com.project.chirp.api.dto.ChatDto
import com.project.chirp.api.dto.CreateChatRequest
import com.project.chirp.api.mappers.toChatDto
import com.project.chirp.api.util.requestUserId
import com.project.chirp.service.ChatService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/***
 * Controller for chat-related operations.
 * @see createChat Creates a chat between the creator and other participants.
 *
 */
@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService
) {

    @PostMapping
    fun createChat(
        @Valid @RequestBody body: CreateChatRequest
    ): ChatDto {
        return chatService.createChat(
            creatorId = requestUserId,
            otherUserIds = body.otherUserIds.toSet()
        ).toChatDto()
    }
}