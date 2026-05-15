package com.team7.eventticketing.event.messaging.publisher;

import com.team7.eventticketing.event.messaging.config.RabbitMQConfig;
import com.team7.eventticketing.contracts.events.EventRatedEvent;
import com.team7.eventticketing.contracts.events.EventStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStatusChanged(EventStatusChangedEvent event) {
        publish(RabbitMQConfig.EVENT_STATUS_CHANGED, event, event.eventId(), null);
    }

    public void publishRated(EventRatedEvent event) {
        publish(RabbitMQConfig.EVENT_RATED, event, event.eventId(), event.bookingId());
    }

    private void publish(String routingKey, Object payload, Long eventId, Long bookingId) {
        try {
            MDC.put("routingKey", routingKey);

            if (eventId != null) {
                MDC.put("eventId", eventId.toString());
            }

            if (bookingId != null) {
                MDC.put("bookingId", bookingId.toString());
            }

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EVENT_EXCHANGE,
                    routingKey,
                    payload,
                    message -> {
                        String correlationId = MDC.get("correlationId");
                        if (correlationId != null) {
                            message.getMessageProperties()
                                    .setHeader("X-Correlation-ID", correlationId);
                        }

                        message.getMessageProperties()
                                .setHeader("routingKey", routingKey);

                        return message;
                    }
            );

            log.info("Published {} for eventId={}", routingKey, eventId);

        } finally {
            MDC.remove("routingKey");
            MDC.remove("eventId");
            MDC.remove("bookingId");
        }
    }
}