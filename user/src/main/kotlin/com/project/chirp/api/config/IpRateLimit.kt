package com.project.chirp.api.config

import java.util.concurrent.TimeUnit

/**
 * Annotation for IP-based rate limiting.
 *
 * @param requests The maximum number of requests allowed per IP before rate limiting.
 * @param duration The duration after which the rate limit resets.
 * @param unit The time unit for the duration.
 */
annotation class IpRateLimit(
    val requests: Int = 60,
    val duration: Long = 1L,
    val unit: TimeUnit = TimeUnit.MINUTES
)