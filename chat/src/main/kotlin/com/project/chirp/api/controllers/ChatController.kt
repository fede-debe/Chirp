package com.project.chirp.api.controllers

import com.project.chirp.api.dto.AddParticipantToChatDto
import com.project.chirp.api.dto.ChatDto
import com.project.chirp.api.dto.CreateChatRequest
import com.project.chirp.api.mappers.toChatDto
import com.project.chirp.api.util.requestUserId
import com.project.chirp.domain.type.ChatId
import com.project.chirp.service.ChatService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/***
 * Controller for chat-related operations.
 * @see createChat Creates a chat between the creator and other participants.
 * @see addChatParticipants Adds participants to an existing chat.
 * @see leaveChat Removes the current user from a chat.
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

    @PostMapping("/{chatId}/add")
    fun addChatParticipants(
        @PathVariable chatId: ChatId,
        @Valid @RequestBody body: AddParticipantToChatDto
    ): ChatDto {
        return chatService.addParticipantsToChat(
            requestUserId = requestUserId,
            chatId = chatId,
            userIds = body.userIds.toSet()
        ).toChatDto()
    }

    @DeleteMapping("/{chatId}/leave")
    fun leaveChat(
        @PathVariable chatId: ChatId
    ) {
        chatService.removeParticipantFromChat(
            chatId = chatId,
            userId = requestUserId
        )
    }
}