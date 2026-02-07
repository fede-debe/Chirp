package com.project.chirp.domain.exception

class UserAlreadyExistsException : RuntimeException(
    "A user with that email or username already exists."
)