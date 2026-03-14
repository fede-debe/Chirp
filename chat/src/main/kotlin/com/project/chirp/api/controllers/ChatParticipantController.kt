package com.project.chirp.api.controllers

import com.project.chirp.api.dto.ChatParticipantDto
import com.project.chirp.api.dto.ConfirmProfilePictureRequest
import com.project.chirp.api.dto.PictureUploadResponse
import com.project.chirp.api.mappers.toChatParticipantDto
import com.project.chirp.api.mappers.toResponse
import com.project.chirp.api.util.requestUserId
import com.project.chirp.service.ChatParticipantService
import com.project.chirp.service.ProfilePictureService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/***
 * Controller for chat participant-related operations.
 * @see getChatParticipantByUsernameOrEmail Retrieves a chat participant by their username or email.
 * @see getProfilePictureUploadUrl Generates a URL for uploading a profile picture.
 * @see confirmProfilePictureUpload Confirms the upload of a profile picture.
 * @see deleteProfilePicture Deletes a profile picture for a user.
 */
@RestController
@RequestMapping("/api/participants")
class ChatParticipantController(
    private val chatParticipantService: ChatParticipantService,
    private val profilePictureService: ProfilePictureService
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

    /***
     * Generates a URL for uploading a profile picture.
     * @param mimeType The MIME type of the profile picture.
     * It is up to the client which types it supports.
     * @return The URL for uploading the profile picture.
     */
    @PostMapping("/profile-picture-upload")
    fun getProfilePictureUploadUrl(
        @RequestParam mimeType: String
    ): PictureUploadResponse {
        return profilePictureService.generateUploadCredentials(
            userId = requestUserId,
            mimeType = mimeType
        ).toResponse()
    }

    /***
     * Confirms the upload of a profile picture for the currently authenticated user.
     * @param body The request body containing the public URL of the uploaded profile picture.
     */
    @PostMapping("/confirm-profile-picture")
    fun confirmProfilePictureUpload(
        @Valid @RequestBody body: ConfirmProfilePictureRequest
    ) {
        profilePictureService.confirmProfilePictureUpload(
            userId = requestUserId,
            publicUrl = body.publicUrl
        )
    }

    /***
     * Deletes a profile picture for a user.
     */
    @DeleteMapping("/profile-picture")
    fun deleteProfilePicture() {
        profilePictureService.deleteProfilePicture(
            userId = requestUserId
        )
    }
}