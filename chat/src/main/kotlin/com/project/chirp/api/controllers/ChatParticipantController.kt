package com.project.chirp.api.controllers

import com.project.chirp.api.dto.ChatParticipantDto
import com.project.chirp.api.mappers.toChatParticipantDto
import com.project.chirp.api.util.requestUserId
import com.project.chirp.service.ChatParticipantService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/***
 * Controller for chat participant-related operations.
 * @see getChatParticipantByUsernameOrEmail Retrieves a chat participant by their username or email.
 *
 */
@RestController
@RequestMapping("/api/chat/participants")
class ChatParticipantController(
    private val chatParticipantService: ChatParticipantService
) {

    /**
     * Retrieves a chat participant by their username or email.
     * @param query The username or email of the chat participant.
     * @return The chat participant.
     * @throws ResponseStatusException If the chat participant is not found.
     *
     * This endpoint will handle 2 different scenarios:
     * - If the query parameter is not provided, it will return the chat participant for the currently authenticated user.
     * - If the query parameter is provided, it will return the chat participant with the given username or email.
     */
    @GetMapping
    fun getChatParticipantByUsernameOrEmail(
        @RequestParam(required = false) query: String?
    ): ChatParticipantDto {
        val participant = if (query == null) {
            chatParticipantService.findChatParticipantById(requestUserId)
        } else {
            chatParticipantService.findChatParticipantByEmailOrUsername(query)
        }

        return participant?.toChatParticipantDto()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}