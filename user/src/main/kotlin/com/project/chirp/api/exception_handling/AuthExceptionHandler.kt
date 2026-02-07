package com.project.chirp.api.exception_handling

import com.project.chirp.domain.exception.InvalidCredentialsException
import com.project.chirp.domain.exception.InvalidTokenException
import com.project.chirp.domain.exception.UserAlreadyExistsException
import com.project.chirp.domain.exception.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/***
 * Handles exceptions related to authentication and authorization.
 *
 * @see onUserAlreadyExists: Handles UserAlreadyExistsException.
 * @see onUserNotFound: Handles UserNotFoundException.
 * @see onInvalidCredentials: Handles InvalidCredentialsException.
 * @see onInvalidToken: Handles InvalidTokenException.
 * @see onValidationException: Handles MethodArgumentNotValidException.
 */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onUserAlreadyExists(
        e: UserAlreadyExistsException
    ) = mapOf(
        "code" to "USER_EXISTS",
        "message" to e.message
    )

    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onUserNotFound(
        e: UserNotFoundException
    ) = mapOf(
        "code" to "USER_NOT_FOUND",
        "message" to e.message
    )

    @ExceptionHandler(InvalidCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun onInvalidCredentials(
        e: InvalidCredentialsException
    ) = mapOf(
        "code" to "INVALID_CREDENTIALS",
        "message" to e.message
    )

    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun onInvalidToken(
        e: InvalidTokenException
    ) = mapOf(
        "code" to "INVALID_TOKEN",
        "message" to e.message
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onValidationException(
        e: MethodArgumentNotValidException
    ): ResponseEntity<Map<String, Any>> {
        /** this has information about which fields are invalid */
        val errors = e.bindingResult.allErrors.map {
            it.defaultMessage ?: "Invalid value"
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "code" to "VALIDATION_ERROR",
                    "errors" to errors
                )
            )
    }
}