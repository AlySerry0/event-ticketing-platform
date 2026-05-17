package com.team7.eventticketing.ticket.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BookingEventConsumerConfig {

    // ── Exchange names ──────────────────────────────────────────────────────────
    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";


    public static final String TICKET_DLX = "ticket.events.dlx";

    // ── Queue names ─────────────────────────────────────────────────────────────
    public static final String SAGA_LISTENER_QUEUE = "ticket.booking.saga-listener";
    public static final String SAGA_LISTENER_DLQ   = "ticket.booking.saga-listener.dlq";

    // ── Routing keys consumed from booking.events ────────────────────────────────
    public static final String RK_BOOKING_PLACED    = "booking.placed";
    public static final String RK_BOOKING_COMPLETED = "booking.completed";
    public static final String RK_BOOKING_CANCELLED = "booking.cancelled";

    // ── Also consume event.status-changed (from event.events exchange) ────────────
    public static final String EVENT_EVENTS_EXCHANGE = "event.events";
    public static final String RK_EVENT_STATUS_CHANGED = "event.status-changed";

    // ── Exchanges ────────────────────────────────────────────────────────────────
    @Bean
    public TopicExchange bookingEventsExchangeRef() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange eventEventsExchangeRef() {
        return new TopicExchange(EVENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ticketDeadLetterExchange() {
        return new DirectExchange(TICKET_DLX, true, false);
    }

    // ── Main consumer queue ──────────────────────────────────────────────────────
    @Bean
    public Queue ticketBookingSagaListenerQueue() {
        return QueueBuilder.durable(SAGA_LISTENER_QUEUE)
                .withArgument("x-dead-letter-exchange", TICKET_DLX)
                .withArgument("x-dead-letter-routing-key", SAGA_LISTENER_DLQ)
                .build();
    }

    // ── Dead-letter queue ────────────────────────────────────────────────────────
    @Bean
    public Queue ticketBookingSagaListenerDlq() {
        return QueueBuilder.durable(SAGA_LISTENER_DLQ).build();
    }

    @Bean
    public Binding dlqBinding(Queue ticketBookingSagaListenerDlq,
                              DirectExchange ticketDeadLetterExchange) {
        return BindingBuilder.bind(ticketBookingSagaListenerDlq)
                .to(ticketDeadLetterExchange)
                .with(SAGA_LISTENER_DLQ);
    }

    // ── Bindings: booking.events → main queue ───────────────────────────────────
    @Bean
    public Binding bookingPlacedBinding(Queue ticketBookingSagaListenerQueue,
                                        TopicExchange bookingEventsExchangeRef) {
        return BindingBuilder.bind(ticketBookingSagaListenerQueue)
                .to(bookingEventsExchangeRef)
                .with(RK_BOOKING_PLACED);
    }

    @Bean
    public Binding bookingCompletedBinding(Queue ticketBookingSagaListenerQueue,
                                           TopicExchange bookingEventsExchangeRef) {
        return BindingBuilder.bind(ticketBookingSagaListenerQueue)
                .to(bookingEventsExchangeRef)
                .with(RK_BOOKING_COMPLETED);
    }

    @Bean
    public Binding bookingCancelledBinding(Queue ticketBookingSagaListenerQueue,
                                           TopicExchange bookingEventsExchangeRef) {
        return BindingBuilder.bind(ticketBookingSagaListenerQueue)
                .to(bookingEventsExchangeRef)
                .with(RK_BOOKING_CANCELLED);
    }

    // ── Binding: event.events → main queue ──────────────────────────────────────
    @Bean
    public Binding eventStatusChangedBinding(Queue ticketBookingSagaListenerQueue,
                                             TopicExchange eventEventsExchangeRef) {
        return BindingBuilder.bind(ticketBookingSagaListenerQueue)
                .to(eventEventsExchangeRef)
                .with(RK_EVENT_STATUS_CHANGED);
    }
}
