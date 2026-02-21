package com.project.chirp.domain.events

import com.project.chirp.domain.events.user.UserEvent
import com.project.chirp.domain.events.user.UserEventConstants
import java.time.Instant

/***
 * This interface defines the basic structure of a Chirp event.
 * We get the event, exchange will process it and reroute it to the correct queue.
 * @param eventId: The unique identifier for the event.
 * @param eventKey: The key that identifies the type of event (verified, registered, new message, etc.).
 * @param occurredAt: The time when the event occurred.
 * @param exchange: The exchange where the event is published. Receives all kind of related events, process these and reroute these to clear queues.
 *
 * @see UserEvent With this ChirpEvent interface, we can define a very specific implementation which is a sealed hierarchy for events related to users.
 * @see UserEventConstants for those user events, we want to define some constants to make it easier to identify the type of event.
 */
interface ChirpEvent {
    val eventId: String
    val eventKey: String
    val occurredAt: Instant
    val exchange: String
}