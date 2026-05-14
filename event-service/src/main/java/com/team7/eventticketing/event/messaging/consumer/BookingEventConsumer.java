package com.team7.eventticketing.event.messaging.consumer;

import com.team7.eventticketing.contracts.events.BookingCancelledEvent;
import com.team7.eventticketing.contracts.events.BookingCompletedEvent;
import com.team7.eventticketing.contracts.events.BookingPlacedEvent;
import com.team7.eventticketing.event.messaging.config.RabbitMQConfig;
import com.team7.eventticketing.event.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(BookingEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final EventService eventService;

    public BookingEventConsumer(ObjectMapper objectMapper, EventService eventService) {
        this.objectMapper = objectMapper;
        this.eventService = eventService;
    }

    @RabbitListener(queues = RabbitMQConfig.BOOKING_SAGA_QUEUE)
    public void consumeBookingEvents(Message message) {

        String routingKey = message.getMessageProperties()
                .getReceivedRoutingKey();

        try {

            switch (routingKey) {

                case RabbitMQConfig.BOOKING_PLACED -> {
                    BookingPlacedEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    BookingPlacedEvent.class
                            );

                    withMdc(
                            message,
                            event.eventId(),
                            event.bookingId(),
                            routingKey,
                            () -> {

                                log.info(
                                        "Consuming booking.placed for bookingId={} eventId={}",
                                        event.bookingId(),
                                        event.eventId()
                                );

                                eventService.invalidateBookingDependentCaches(event.eventId());

                                log.info(
                                        "Processed booking.placed for bookingId={} eventId={}",
                                        event.bookingId(),
                                        event.eventId()
                                );
                            }
                    );
                }

                case RabbitMQConfig.BOOKING_COMPLETED -> {
                    BookingCompletedEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    BookingCompletedEvent.class
                            );

                    withMdc(
                            message,
                            event.eventId(),
                            event.bookingId(),
                            routingKey,
                            () -> {

                                log.info(
                                        "Consuming booking.completed for bookingId={} eventId={}",
                                        event.bookingId(),
                                        event.eventId()
                                );

                                eventService.invalidateBookingDependentCaches(event.eventId());

                                log.info(
                                        "Processed booking.completed for bookingId={} eventId={}",
                                        event.bookingId(),
                                        event.eventId()
                                );
                            }
                    );
                }

                case RabbitMQConfig.BOOKING_CANCELLED -> {
                    BookingCancelledEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    BookingCancelledEvent.class
                            );

                    withMdc(
                            message,
                            event.eventId(),
                            event.bookingId(),
                            routingKey,
                            () -> {

                                log.info(
                                        "Consuming booking.cancelled for bookingId={} eventId={} reason={}",
                                        event.bookingId(),
                                        event.eventId(),
                                        event.reason()
                                );

                                eventService.invalidateBookingDependentCaches(event.eventId());

                                log.info(
                                        "Processed booking.cancelled for bookingId={} eventId={}",
                                        event.bookingId(),
                                        event.eventId()
                                );
                            }
                    );
                }

                default -> log.warn(
                        "Unknown routing key received: {}",
                        routingKey
                );
            }

        } catch (Exception e) {

            log.error(
                    "Failed to process {}: {}",
                    routingKey,
                    e.getMessage()
            );

            throw new RuntimeException(e);
        }
    }

    private void withMdc(
            Message message,
            Long eventId,
            Long bookingId,
            String routingKey,
            Runnable action
    ) {

        try {

            Object correlationId =
                    message.getMessageProperties()
                            .getHeaders()
                            .get("X-Correlation-ID");

            if (correlationId != null) {
                MDC.put("correlationId", correlationId.toString());
            }

            if (eventId != null) {
                MDC.put("eventId", eventId.toString());
            }

            if (bookingId != null) {
                MDC.put("bookingId", bookingId.toString());
            }

            MDC.put("routingKey", routingKey);

            action.run();

        } finally {

            MDC.remove("correlationId");
            MDC.remove("eventId");
            MDC.remove("bookingId");
            MDC.remove("routingKey");
        }
    }
}