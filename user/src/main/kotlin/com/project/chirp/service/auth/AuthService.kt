package org.example.com.project.chirp.service.auth

import com.project.chirp.domain.model.User
import com.project.chirp.infra.database.entities.UserEntity
import com.project.chirp.infra.database.repositories.UserRepository
import org.example.com.project.chirp.domain.exception.UserAlreadyExistsException
import org.example.com.project.chirp.infra.database.mappers.toUser
import org.example.com.project.chirp.infra.security.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun register(email: String, username: String, password: String): User {
        val user = userRepository.findByEmailOrUsername(
            email = email.trim(),
            username = username.trim()
        )
        /** Handle error with exception */
        if (user != null) {
            throw UserAlreadyExistsException()
        }

        /** calling save would also upsert the user based on primary id */
        val savedUser = userRepository.save(
            UserEntity(
                email = email.trim(),
                username = username.trim(),
                hashedPassword = passwordEncoder.encode(password)!!,

                )
        ).toUser()

        return savedUser
    }
}