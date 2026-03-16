package com.project.chirp.service

import com.project.chirp.api.dto.UpdateUserSettingsRequest
import com.project.chirp.domain.exception.UserNotFoundException
import com.project.chirp.domain.model.User
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.mappers.toUser
import com.project.chirp.infra.database.repositories.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository
) {

    @Transactional
    fun updateUserSettings(userId: UserId, request: UpdateUserSettingsRequest): User {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException()

        user.typingIndicatorsEnabled = request.typingIndicatorsEnabled

        return userRepository.save(user).toUser()
    }
}
