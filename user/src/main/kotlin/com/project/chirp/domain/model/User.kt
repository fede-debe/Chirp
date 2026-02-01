package com.project.chirp.domain.model

import java.util.*

/** we can reference UserId and have a single place
 * where we can change the type of this UserId.
 * In case we want to change the type of UserId we
 * can do it in a single place.
 * */
typealias UserId = UUID

data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val hasEmailVerified: Boolean
)
