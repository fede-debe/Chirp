package com.project.chirp.api.mappers

import com.project.chirp.api.dto.AuthenticatedUserDto
import com.project.chirp.api.dto.UserDto
import com.project.chirp.domain.model.AuthenticatedUser
import com.project.chirp.domain.model.User

/** If implementation details of what the JSON fields name are, for example
 * in UserDto username became username_v2, the domain module would not need
 * to change its implementation.
 * */

fun AuthenticatedUser.toAuthenticatedUserDto(): AuthenticatedUserDto {
    return AuthenticatedUserDto(
        user = user.toUserDto(),
        accessToken = accessToken,
        refreshToken = refreshToken,
    )
}

fun User.toUserDto(): UserDto {
    return UserDto(
        id = id,
        email = email,
        username = username,
        hasEmailVerified = hasEmailVerified,
        typingIndicatorsEnabled = typingIndicatorsEnabled,
    )
}
