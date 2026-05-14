package com.team7.eventticketing.event.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // =========================================================
    // EXCHANGES
    // =========================================================

    public static final String EVENT_EXCHANGE = "event.events";

    public static final String EVENT_DLX = "event.events.dlx";

    // =========================================================
    // QUEUES
    // =========================================================

    public static final String BOOKING_SAGA_QUEUE =
            "event.booking.saga-listener";

    public static final String BOOKING_SAGA_DLQ =
            "event.booking.saga-listener.dlq";

    // =========================================================
    // ROUTING KEYS
    // =========================================================

    public static final String BOOKING_PLACED =
            "booking.placed";

    public static final String BOOKING_COMPLETED =
            "booking.completed";

    public static final String BOOKING_CANCELLED =
            "booking.cancelled";

    public static final String DLQ_ROUTING_KEY =
            "event.booking.dlq";

    public static final String EVENT_STATUS_CHANGED =
            "event.status-changed";

    public static final String EVENT_RATED =
            "event.rated";

    // =========================================================
    // EXCHANGE BEANS
    // =========================================================

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EVENT_EXCHANGE);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(EVENT_DLX);
    }

    // =========================================================
    // QUEUE BEANS
    // =========================================================

    @Bean
    public Queue bookingSagaQueue() {

        return QueueBuilder.durable(BOOKING_SAGA_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EVENT_DLX,
                        "x-dead-letter-routing-key", DLQ_ROUTING_KEY
                ))
                .build();
    }

    @Bean
    public Queue bookingSagaDeadLetterQueue() {
        return QueueBuilder.durable(BOOKING_SAGA_DLQ).build();
    }

    // =========================================================
    // BINDINGS
    // =========================================================

    @Bean
    public Binding bookingPlacedBinding() {
        return BindingBuilder
                .bind(bookingSagaQueue())
                .to(eventExchange())
                .with(BOOKING_PLACED);
    }

    @Bean
    public Binding bookingCompletedBinding() {
        return BindingBuilder
                .bind(bookingSagaQueue())
                .to(eventExchange())
                .with(BOOKING_COMPLETED);
    }

    @Bean
    public Binding bookingCancelledBinding() {
        return BindingBuilder
                .bind(bookingSagaQueue())
                .to(eventExchange())
                .with(BOOKING_CANCELLED);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder
                .bind(bookingSagaDeadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }
}