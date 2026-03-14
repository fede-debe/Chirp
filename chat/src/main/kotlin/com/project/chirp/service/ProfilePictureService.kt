package com.project.chirp.service

import com.project.chirp.domain.event.ProfilePictureUpdatedEvent
import com.project.chirp.domain.exception.ChatParticipantNotFoundException
import com.project.chirp.domain.models.ProfilePictureUploadCredentials
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.repositories.ChatParticipantRepository
import com.project.chirp.infra.storage.SupabaseStorageService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/***
 * Service for managing profile picture-related operations.
 * @see generateUploadCredentials Generates signed upload credentials for profile pictures.
 * @see deleteProfilePicture Deletes a profile picture for a user.
 * @see confirmProfilePictureUpload Confirms the upload of a profile picture for a user.
 *
 * @param supabaseStorageService: Service for interacting with Supabase storage.
 * @param chatParticipantRepository: Repository for managing chat participant entities.
 * @param applicationEventPublisher: Publisher for application events.
 */
@Service
class ProfilePictureService(
    private val supabaseStorageService: SupabaseStorageService,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(ProfilePictureService::class.java)

    fun generateUploadCredentials(
        userId: UserId,
        mimeType: String,
    ): ProfilePictureUploadCredentials {
        return supabaseStorageService.generateSignedUploadUrl(
            userId = userId,
            mimeType = mimeType
        )
    }

    @Transactional
    fun deleteProfilePicture(userId: UserId) {
        // participant that needs to delete their profile picture
        val participant = chatParticipantRepository.findByIdOrNull(userId)
            ?: throw ChatParticipantNotFoundException(userId)

        participant.profilePictureUrl?.let { url ->
            // first we delete the file from db
            chatParticipantRepository.save(
                participant.apply { profilePictureUrl = null }
            )

            // then we delete the file from storage
            supabaseStorageService.deleteFile(url)

            applicationEventPublisher.publishEvent(
                ProfilePictureUpdatedEvent(
                    userId = userId,
                    newUrl = null
                )
            )
        }
    }

    @Transactional
    fun confirmProfilePictureUpload(userId: UserId, publicUrl: String) {
        val participant = chatParticipantRepository.findByIdOrNull(userId)
            ?: throw ChatParticipantNotFoundException(userId)

        // before making the update to delete it from storage
        val oldUrl = participant.profilePictureUrl

        chatParticipantRepository.save(
            participant.apply { profilePictureUrl = publicUrl }
        )

        // we don't want to fail the whole operation if deleting the old file fails since the function is annotated with @Transactional
        try {
            oldUrl?.let {
                supabaseStorageService.deleteFile(oldUrl)
            }
        } catch (e: Exception) {
            logger.warn("Deleting old profile picture for $userId failed", e)
        }

        applicationEventPublisher.publishEvent(
            ProfilePictureUpdatedEvent(
                userId = userId,
                newUrl = publicUrl
            )
        )
    }
}