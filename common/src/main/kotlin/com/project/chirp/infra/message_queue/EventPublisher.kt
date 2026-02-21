package com.project.chirp.infra.message_queue

import com.project.chirp.domain.events.ChirpEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

/**
 * Publishes ChirpEvents to RabbitMQ.
 *
 * This class is responsible for publishing ChirpEvents to RabbitMQ.
 * It uses a RabbitTemplate to send events to the appropriate queues.
 *
 * @param rabbitTemplate The RabbitTemplate used for publishing events.
 */
@Component
class EventPublisher(
    private val rabbitTemplate: RabbitTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T : ChirpEvent> publish(event: T) {
        try {
            rabbitTemplate.convertAndSend(
                event.exchange,
                event.eventKey,
                event
            )
            logger.info("Successfully published event: ${event.eventKey}")
        } catch (e: Exception) {
            logger.error("Failed to publish ${event.eventKey} event", e)
        }
    }
}