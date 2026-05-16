package com.team7.eventticketing.ticket.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team7.eventticketing.contracts.events.BookingCancelledEvent;
import com.team7.eventticketing.contracts.events.BookingCompletedEvent;
import com.team7.eventticketing.contracts.events.BookingPlacedEvent;
import com.team7.eventticketing.ticket.config.BookingEventConsumerConfig;
import com.team7.eventticketing.ticket.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    public BookingEventConsumer(TicketService ticketService, ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = BookingEventConsumerConfig.SAGA_LISTENER_QUEUE)
    public void onBookingEvent(Message message,
                               @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String correlationId = (String) message.getMessageProperties().getHeaders().get("X-Correlation-ID");
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
        MDC.put("routingKey", routingKey);

        try {
            byte[] body = message.getBody();

            switch (routingKey) {
                case BookingEventConsumerConfig.RK_BOOKING_PLACED -> {
                    BookingPlacedEvent event = objectMapper.readValue(body, BookingPlacedEvent.class);
                    MDC.put("bookingId", String.valueOf(event.bookingId()));
                    MDC.put("eventId", String.valueOf(event.eventId()));
                    log.info("Consuming {} for bookingId={}", routingKey, event.bookingId());
                    ticketService.captureEventIdForBooking(event.bookingId(), event.eventId());
                    log.info("Processed {} for bookingId={}", routingKey, event.bookingId());
                }
                case BookingEventConsumerConfig.RK_BOOKING_COMPLETED -> {
                    BookingCompletedEvent event = objectMapper.readValue(body, BookingCompletedEvent.class);
                    MDC.put("bookingId", String.valueOf(event.bookingId()));
                    log.info("Consuming {} for bookingId={}", routingKey, event.bookingId());
                    ticketService.auditTicketsForCompletedBooking(event.bookingId());
                    log.info("Processed {} for bookingId={}", routingKey, event.bookingId());
                }
                case BookingEventConsumerConfig.RK_BOOKING_CANCELLED -> {
                    BookingCancelledEvent event = objectMapper.readValue(body, BookingCancelledEvent.class);
                    MDC.put("bookingId", String.valueOf(event.bookingId()));
                    log.info("Consuming {} for bookingId={}", routingKey, event.bookingId());
                    ticketService.cancelTicketsForBooking(event.bookingId());
                    log.info("Processed {} for bookingId={}", routingKey, event.bookingId());
                }
                case BookingEventConsumerConfig.RK_EVENT_STATUS_CHANGED -> {
                    log.info("Consuming {} — no action required for ticket-service", routingKey);
                    log.info("Processed {} for event updates", routingKey);
                }
                default -> log.warn("Unhandled routing key [{}] on saga listener queue — discarding", routingKey);
            }

        } catch (Exception e) {
            log.error("Failed to process {}: {}", routingKey, e.getMessage());
            throw new RuntimeException("Consumer failed for routingKey=" + routingKey, e);
        } finally {
            MDC.clear();
        }
    }
}
