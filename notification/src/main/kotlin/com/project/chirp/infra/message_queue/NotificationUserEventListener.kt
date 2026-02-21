package com.project.chirp.infra.message_queue

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.service.EmailService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Duration

/***
 * Listens for UserEvents and prints a message for each event.
 *
 * We need to @RabbitListener to listen for events on the queue.
 *
 */
@Component
class NotificationUserEventListener(private val emailService: EmailService) {

    @RabbitListener(
        queues = [MessageQueues.NOTIFICATION_USER_EVENTS],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun handleUserEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                emailService.sendVerificationEmail(
                    email = event.email,
                    username = event.username,
                    userId = event.userId,
                    token = event.verificationToken
                )
            }

            is UserEvent.RequestResendVerification -> {
                emailService.sendVerificationEmail(
                    email = event.email,
                    username = event.username,
                    userId = event.userId,
                    token = event.verificationToken
                )
            }

            is UserEvent.RequestResetPassword -> {
                emailService.sendPasswordResetEmail(
                    email = event.email,
                    username = event.username,
                    userId = event.userId,
                    token = event.passwordResetToken,
                    expiresIn = Duration.ofMinutes(event.expiresInMinutes)
                )
            }

            else -> Unit
        }
    }
}