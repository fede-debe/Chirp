package com.project.chirp.domain.model

/***
 * Identifies how an account authenticates.
 * - [EMAIL]: classic email + password (has a hashedPassword).
 * - [GOOGLE] / [APPLE]: social sign-in (password-less; identified by providerId = the provider `sub`).
 */
enum class AuthProvider {
    EMAIL,
    GOOGLE,
    APPLE,
}
