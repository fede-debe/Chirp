package com.project.chirp.api.exception_handling

import com.project.chirp.domain.exception.InvalidDeviceTokenException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/***
 * Handles exceptions related to notification-related operations.
 *
 * @see onInvalidDeviceToken: Handles InvalidDeviceTokenException.
 *
 */
@RestControllerAdvice
class NotificationExceptionHandler {

    @ExceptionHandler(InvalidDeviceTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onInvalidDeviceToken(e: InvalidDeviceTokenException) = mapOf(
        "code" to "INVALID_DEVICE_TOKEN",
        "message" to e.message
    )
}