package com.project.chirp.domain.events.user

import com.project.chirp.domain.events.ChirpEvent
import com.project.chirp.domain.type.UserId
import java.time.Instant
import java.util.*

/***
 * This sealed class defines the different types of user events.
 * @param eventId: The unique identifier for the event.
 * @param exchange: The exchange where the event is published. Receives all kind of related events, process these and reroute them to clear queues.
 * @param occurredAt: The time when the event occurred.
 *
 * @see UserEventConstants for each constant declared within the UserEventConstants object, we can create here a corresponding event.
 */
sealed class UserEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val exchange: String = UserEventConstants.USER_EXCHANGE,
    override val occurredAt: Instant = Instant.now(),
) : ChirpEvent {

    /***
     * This event is triggered when a new user is created.
     * @param userId: The unique identifier for the user.
     * @param email: The email of the user.
     * @param username: The username of the user.
     * @param verificationToken: The verification token for the user.
     */
    data class Created(
        val userId: UserId,
        val email: String,
        val username: String,
        val verificationToken: String,
        override val eventKey: String = UserEventConstants.USER_CREATED_KEY
    ) : UserEvent(), ChirpEvent

    /***
     * This event is sent when a user verifies their email.
     * @param userId: The unique identifier for the user.
     * @param email: The email of the user.
     * @param username: The username of the user.
     */
    data class Verified(
        val userId: UserId,
        val email: String,
        val username: String,
        override val eventKey: String = UserEventConstants.USER_VERIFIED
    ) : UserEvent(), ChirpEvent

    /**
     * Event sent when a user requests to resend their verification email.
     * @param userId: The unique identifier for the user.
     * @param email: The email of the user.
     * @param username: The username of the user.
     * @param verificationToken: The verification token for the user.
     */
    data class RequestResendVerification(
        val userId: UserId,
        val email: String,
        val username: String,
        val verificationToken: String,
        override val eventKey: String = UserEventConstants.USER_REQUEST_RESEND_VERIFICATION
    ) : UserEvent(), ChirpEvent

    /***
     * This event is sent when a user requests to reset their password.
     * @param userId: The unique identifier for the user.
     * @param email: The email of the user.
     * @param username: The username of the user.
     * @param passwordResetToken: The password reset token for the user.
     * @param expiresInMinutes: The time in minutes until the password reset token expires.
     */
    data class RequestResetPassword(
        val userId: UserId,
        val email: String,
        val username: String,
        val passwordResetToken: String,
        val expiresInMinutes: Long,
        override val eventKey: String = UserEventConstants.USER_REQUEST_RESET_PASSWORD
    ) : UserEvent(), ChirpEvent
}