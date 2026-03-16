package com.project.chirp.api.controllers

import com.project.chirp.api.dto.BatchAttachmentUploadRequest
import com.project.chirp.api.util.requestUserId
import com.project.chirp.domain.exception.ForbiddenException
import com.project.chirp.domain.models.ProfilePictureUploadCredentials
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.ChatMessageId
import com.project.chirp.infra.database.repositories.ChatRepository
import com.project.chirp.infra.storage.SupabaseStorageService
import com.project.chirp.service.ChatMessageService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/***
 * Controller for chat message-related operations.
 * @see deleteMessage Deletes a chat message.
 * @see getAttachmentUploadUrl Generates a signed upload URL for a message attachment.
 */
@RestController
@RequestMapping("/api/messages")
class ChatMessageController(
    private val chatMessageService: ChatMessageService,
    private val supabaseStorageService: SupabaseStorageService,
    private val chatRepository: ChatRepository
) {

    /***
     * Deletes a chat message.
     * Only the original sender is allowed to delete their own message — enforcement is delegated
     * to ChatMessageService which throws ForbiddenException for unauthorized attempts.
     * @param messageId: The ID of the message to delete.
     */
    @DeleteMapping("/{messageId}")
    fun deleteMessage(
        @PathVariable("messageId") messageId: ChatMessageId
    ) {
        chatMessageService.deleteMessage(messageId, requestUserId)
    }

    /***
     * Generates a short-lived Supabase signed upload URL for a message attachment.
     *
     * The client must call this endpoint before sending a message with attachments:
     * 1) Request a signed URL here.
     * 2) Upload the file directly from the client to Supabase using the returned uploadUrl.
     * 3) Include the returned publicUrl in the message payload sent over WebSocket.
     *
     * Participant membership is verified here rather than relying solely on the WebSocket
     * handler because this is a stateless HTTP endpoint with no session context — we must
     * re-validate that the requesting user belongs to the target chat to prevent users from
     * generating upload credentials for chats they are not part of.
     *
     * @param chatId: The ID of the chat the attachment will be sent in.
     * @param mimeType: The MIME type of the file to upload. Must be an allowed image type.
     * @return ProfilePictureUploadCredentials containing the signed upload URL, the future
     *   public URL, required request headers, and the credential expiry timestamp.
     * @throws ForbiddenException: If the requesting user is not a participant of the chat.
     */
    @GetMapping("/attachments/upload-url")
    fun getAttachmentUploadUrl(
        @RequestParam("chatId") chatId: ChatId,
        @RequestParam("mimeType") mimeType: String
    ): ProfilePictureUploadCredentials {
        val userId = requestUserId
        chatRepository.findChatById(chatId, userId)
            ?: throw ForbiddenException()
        return supabaseStorageService.generateMessageAttachmentUploadUrl(userId, chatId, mimeType)
    }

    /***
     * Generates signed upload URLs for multiple message attachments in a single request.
     *
     * Follows the same flow as getAttachmentUploadUrl but accepts a list of attachment metadata
     * and returns a corresponding list of credentials, one per attachment.
     *
     * @param body: The chat ID and metadata for each file to upload. Limited to 10 attachments.
     * @return List of ProfilePictureUploadCredentials, one per requested attachment, in the same order.
     * @throws ForbiddenException: If the requesting user is not a participant of the chat.
     */
    @PostMapping("/attachments/upload-urls")
    fun getAttachmentUploadUrls(
        @RequestBody @Valid body: BatchAttachmentUploadRequest
    ): List<ProfilePictureUploadCredentials> {
        val userId = requestUserId
        chatRepository.findChatById(body.chatId, userId)
            ?: throw ForbiddenException()
        return supabaseStorageService.generateMessageAttachmentUploadUrls(userId, body.chatId, body.attachments)
    }
}