package com.project.chirp.api.controllers

import com.project.chirp.api.dto.*
import com.project.chirp.api.mappers.toAuthenticatedUserDto
import com.project.chirp.api.mappers.toUserDto
import com.project.chirp.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** This class handles incoming rest requests for authentication
 *
 * @see register: Registers a new user.
 * @see login: Authenticates a user and returns an AuthenticatedUser object.
 */
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

    /*** Authenticates a user and returns an AuthenticatedUser object.
     * @RequestBody body: The login request containing the user's email and password.
     * @return An AuthenticatedUserDto object containing the user's information, access token, and refresh token.
     *
     * business logic is handled by AuthService.login()
     */
    @PostMapping("/login")
    fun login(
        @RequestBody body: LoginRequest
    ): AuthenticatedUserDto {
        return authService.login(
            email = body.email,
            password = body.password
        ).toAuthenticatedUserDto()
    }

    /*** Refreshes an access token using a valid refresh token.
     * @RequestBody body: The refresh request containing the refresh token.
     * @return An AuthenticatedUserDto object containing the refreshed access token and refresh token.
     *
     * business logic is handled by AuthService.refresh()
     */
    @PostMapping("/refresh")
    fun refresh(
        @RequestBody body: RefreshRequest
    ): AuthenticatedUserDto {
        return authService
            .refresh(body.refreshToken)
            .toAuthenticatedUserDto()
    }
}