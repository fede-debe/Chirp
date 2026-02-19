package com.project.chirp.api.controllers

import com.project.chirp.api.dto.*
import com.project.chirp.api.mappers.toAuthenticatedUserDto
import com.project.chirp.api.mappers.toUserDto
import com.project.chirp.infra.rate_limiting.EmailRateLimiter
import com.project.chirp.service.AuthService
import com.project.chirp.service.EmailVerificationService
import com.project.chirp.service.PasswordResetService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/** This class handles incoming rest requests for authentication
 *
 * @see register: Registers a new user.
 * @see login: Authenticates a user and returns an AuthenticatedUser object.
 * @see forgotPassword: Requests a password reset for a user.
 * @see resetPassword: Resets a user's password using a password reset token.
 * @see changePassword: Changes a user's password.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    private val emailRateLimiter: EmailRateLimiter,

    ) {
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

    /*** Logs out a user by invalidating their refresh token.
     * @RequestBody body: The refresh request containing the refresh token.
     */
    @PostMapping("/logout")
    fun logout(
        @RequestBody body: RefreshRequest
    ) {
        authService.logout(body.refreshToken)
    }

    /*** Resends an email verification token for a user.
     * @Valid @RequestBody body: The email request containing the user's email.
     */
    @PostMapping("/resend-verification")
    fun resendVerification(
        @Valid @RequestBody body: EmailRequest
    ) {
        emailRateLimiter.withRateLimit(
            email = body.email
        ) {
            emailVerificationService.resendVerificationEmail(body.email)
        }
    }

    /*** Verifies an email verification token for a user.
     * @RequestParam token: The email verification token to verify.
     */
    @GetMapping("/verify")
    fun verifyEmail(
        @RequestParam token: String
    ) {
        emailVerificationService.verifyEmail(token)
    }

    /*** Requests a password reset for a user.
     * @Valid @RequestBody body: The email request containing the user's email.
     */
    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody body: EmailRequest
    ) {
        passwordResetService.requestPasswordReset(body.email)
    }

    /*** Resets a user's password using a password reset token.
     * @Valid @RequestBody body: The reset password request containing the token and new password.
     */
    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody body: ResetPasswordRequest
    ) {
        passwordResetService.resetPassword(
            token = body.token,
            newPassword = body.newPassword
        )
    }

    /*** Changes a user's password.
     * @Valid @RequestBody body: The change password request containing the old password and new password.
     * The userId is extracted from the JSON Web Token (JWT) in the Authorization header.
     * For each authenticated request, the client will attach the JWT with the user ID.
     */
    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody body: ChangePasswordRequest
    ) {
        // TODO: Extract request user ID and call service
    }

}