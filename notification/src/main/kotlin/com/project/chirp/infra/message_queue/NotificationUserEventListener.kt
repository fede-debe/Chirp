package com.project.chirp.infra.message_queue

import com.project.chirp.domain.events.user.UserEvent
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/***
 * Listens for UserEvents and prints a message for each event.
 *
 * We need to @RabbitListener to listen for events on the queue.
 *
 */
@Component
class NotificationUserEventListener {

    @RabbitListener(queues = [MessageQueues.NOTIFICATION_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                println("User created!")
            }

            is UserEvent.RequestResendVerification -> {
                println("Request resend verification!")
            }

            is UserEvent.RequestResetPassword -> {
                println("Request resend password!")
            }

            else -> Unit
        }
    }
}