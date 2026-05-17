package com.team7.eventticketing.user.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team7.eventticketing.contracts.events.BookingCancelledEvent;
import com.team7.eventticketing.contracts.events.BookingCompletedEvent;
import com.team7.eventticketing.user.messaging.config.UserEventConfig;
import com.team7.eventticketing.user.model.ProcessedEvent;
import com.team7.eventticketing.user.repository.ProcessedEventRepository;
import com.team7.eventticketing.user.util.CacheInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * S1-EVENTS consumer for booking lifecycle events on the user.booking.saga-listener queue.
 *
 * Idempotency: processed_events table guards against RabbitMQ at-least-once redelivery
 * (spec §2.9). The cache invalidation + processed_events insert run inside one @Transactional
 * boundary — if either fails the entire delivery is retried (Spring retry, max-attempts=3),
 * after which the message flows to user.booking.saga-listener.dlq via the queue's
 * x-dead-letter-exchange arg declared in UserEventConfig.
 *
 * Work: invalidate the S1-F3 booking-summary cache for the affected user. We don't maintain
 * denormalized stats locally — S1-F3 derives stats on read via Feign — so the only thing the
 * cache invalidation accomplishes is to ensure the next read sees fresh data from booking-service.
 */
@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public BookingEventConsumer(ObjectMapper objectMapper,
                                ProcessedEventRepository processedEventRepository,
                                CacheInvalidationService cacheInvalidationService) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @RabbitListener(queues = UserEventConfig.SAGA_LISTENER_QUEUE)
    @Transactional
    public void onBookingEvent(Message message) throws Exception {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        switch (routingKey) {
            case UserEventConfig.BOOKING_COMPLETED_KEY -> {
                BookingCompletedEvent event =
                        objectMapper.readValue(message.getBody(), BookingCompletedEvent.class);
                withMdc(message, event.bookingId(), event.userId(), routingKey, () -> {
                    log.info("Consuming {} for bookingId={} userId={} eventId={}",
                            routingKey, event.bookingId(), event.userId(), event.eventId());
                    processIfNew(routingKey, event.bookingId(), event.userId());
                });
            }

            case UserEventConfig.BOOKING_CANCELLED_KEY -> {
                BookingCancelledEvent event =
                        objectMapper.readValue(message.getBody(), BookingCancelledEvent.class);
                withMdc(message, event.bookingId(), event.userId(), routingKey, () -> {
                    log.info("Consuming {} for bookingId={} userId={} eventId={} reason='{}'",
                            routingKey, event.bookingId(), event.userId(),
                            event.eventId(), event.reason());
                    processIfNew(routingKey, event.bookingId(), event.userId());
                });
            }

            default -> log.warn("Unknown routing key received on queue '{}': {}",
                    UserEventConfig.SAGA_LISTENER_QUEUE, routingKey);
        }
    }

    /**
     * Idempotency-guarded mutation: skips silently if this routingKey+bookingId pair has been
     * processed before (RabbitMQ at-least-once redelivery). Otherwise invalidates the S1-F3
     * booking-summary cache for this user and inserts the processed-events row in the same
     * transaction so duplicate consumption never causes duplicate side effects.
     */
    private void processIfNew(String routingKey, Long bookingId, Long userId) {
        String eventKey = routingKey + ":" + bookingId;

        if (processedEventRepository.existsById(eventKey)) {
            log.info("Skipping duplicate '{}' for bookingId={} (already in processed_events)",
                    routingKey, bookingId);
            return;
        }

        // Invalidate the S1-F3 booking-summary cache for this user — booking state changed.
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F3::" + userId);

        // Record that this event has been processed so a retry/redelivery is a no-op.
        processedEventRepository.save(new ProcessedEvent(eventKey, LocalDateTime.now()));

        log.info("Processed '{}' for bookingId={} userId={} — S1-F3 cache invalidated, processed_events recorded",
                routingKey, bookingId, userId);
    }

    /**
     * Populates SLF4J MDC for the duration of the listener invocation so every log line
     * carries the booking + user + correlation context required by the §11 LogQL panels.
     */
    private void withMdc(Message message, Long bookingId, Long userId, String routingKey, Runnable action) {
        try {
            Object correlationId = message.getMessageProperties().getHeaders().get("X-Correlation-ID");
            if (correlationId != null) MDC.put("correlationId", correlationId.toString());
            if (bookingId != null) MDC.put("bookingId", bookingId.toString());
            if (userId != null) MDC.put("userId", userId.toString());
            MDC.put("routingKey", routingKey);
            action.run();
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
            MDC.remove("userId");
            MDC.remove("routingKey");
        }
    }
}