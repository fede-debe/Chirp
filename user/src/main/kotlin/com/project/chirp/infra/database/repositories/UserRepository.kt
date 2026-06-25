package com.project.chirp.infra.database.repositories

import com.project.chirp.domain.model.AuthProvider
import com.project.chirp.domain.type.UserId
import com.project.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, UserId> {
    fun findByEmail(email: String): UserEntity?
    fun findByEmailOrUsername(email: String, username: String): UserEntity?
    fun findByAuthProviderAndProviderId(authProvider: AuthProvider, providerId: String): UserEntity?
    fun existsByUsername(username: String): Boolean
}