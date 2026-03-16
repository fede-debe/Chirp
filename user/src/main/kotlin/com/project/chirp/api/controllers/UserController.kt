package com.project.chirp.api.controllers

import com.project.chirp.api.dto.UpdateUserSettingsRequest
import com.project.chirp.api.dto.UserDto
import com.project.chirp.api.mappers.toUserDto
import com.project.chirp.api.util.requestUserId
import com.project.chirp.service.UserService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @PatchMapping("/settings")
    fun updateSettings(@RequestBody @Valid body: UpdateUserSettingsRequest): UserDto {
        return userService.updateUserSettings(requestUserId, body).toUserDto()
    }
}
