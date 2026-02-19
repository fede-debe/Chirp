package com.project.chirp.domain.exception

/***
 * Thrown when a rate limit has been exceeded.
 *
 * @property resetsInSeconds How many seconds until the rate limit resets
 */
class RateLimitException(
    val resetsInSeconds: Long
) : RuntimeException(
    "Rate limit exceeded. Please try again in $resetsInSeconds seconds."
)