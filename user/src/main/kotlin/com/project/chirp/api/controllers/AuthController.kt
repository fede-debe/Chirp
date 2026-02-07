package com.project.chirp.api.controllers

import com.project.chirp.api.dto.RegisterRequest
import com.project.chirp.api.dto.UserDto
import com.project.chirp.api.mappers.toUserDto
import com.project.chirp.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** This class handles incoming rest requests for authentication*/
@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {
    /* fun to register a new user.
    * @Valid: spring validation would throw an exception if these fields would not match */
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody body: RegisterRequest
    ): UserDto {
        return authService.register(email = body.email, username = body.username, password = body.password).toUserDto()
    }
}