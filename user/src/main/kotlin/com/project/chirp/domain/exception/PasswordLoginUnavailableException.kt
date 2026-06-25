package com.project.chirp.domain.exception

/***
 * Thrown when a password operation (change-password) is attempted on a social, password-less
 * account. Mapped to a clear client error by AuthExceptionHandler.
 */
class PasswordLoginUnavailableException :
    RuntimeException("This account uses social sign-in and has no password")
