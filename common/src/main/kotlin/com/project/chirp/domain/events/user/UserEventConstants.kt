package com.project.chirp.domain.events.user

/***
 * This object contains constants for user events.
 * These constants are used to identify the type of event and to send it to other services that may be interested in.
 */
object UserEventConstants {

    const val USER_EXCHANGE = "user.events"

    const val USER_CREATED_KEY = "user.created"
    const val USER_VERIFIED = "user.verified"
    const val USER_REQUEST_RESEND_VERIFICATION = "user.request_resend_verification"
    const val USER_REQUEST_RESET_PASSWORD = "user.request_reset_password"
}