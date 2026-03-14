package com.project.chirp.domain.exception

class EmailNotVerifiedException(
    val verificationEmailResent: Boolean = false
) : RuntimeException("Email is not verified")