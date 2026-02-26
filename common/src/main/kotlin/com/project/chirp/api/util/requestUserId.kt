package com.project.chirp.api.util

import com.project.chirp.domain.exception.UnauthorizedException
import com.project.chirp.domain.type.UserId
import org.springframework.security.core.context.SecurityContextHolder

/***
 * Extracts the user ID from the request context.
 * Throws an UnauthorizedException if the user ID is not found.
 */
val requestUserId: UserId
    get() = SecurityContextHolder.getContext().authentication?.principal as? UserId
        ?: throw UnauthorizedException()