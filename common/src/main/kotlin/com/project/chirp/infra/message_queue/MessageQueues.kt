package com.project.chirp.infra.message_queue

import com.project.chirp.infra.message_queue.MessageQueues.NOTIFICATION_USER_EVENTS


/***
 * This object contains constants for message queues.
 *
 * @see NOTIFICATION_USER_EVENTS: Queue for notification events related to users. We will extend it later when we also need to reroute
 * messages to other services, like chat.
 */
object MessageQueues {
    const val NOTIFICATION_USER_EVENTS = "notification.user.events"
    const val CHAT_USER_EVENTS = "chat.user.events"
}