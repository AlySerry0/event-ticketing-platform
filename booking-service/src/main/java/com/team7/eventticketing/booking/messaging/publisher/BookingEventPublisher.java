package com.team7.eventticketing.booking.messaging.publisher;

import com.team7.eventticketing.booking.messaging.config.BookingEventConfig;
import com.team7.eventticketing.contracts.events.BookingCancelledEvent;
import com.team7.eventticketing.contracts.events.BookingCompletedEvent;
import com.team7.eventticketing.contracts.events.BookingPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);
	private final RabbitTemplate rabbitTemplate;

	public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publishBookingPlaced(BookingPlacedEvent event) {
		publish(BookingEventConfig.EXCHANGE_NAME, "booking.placed", event.bookingId(), event);
	}

	public void publishBookingCompleted(BookingCompletedEvent event) {
		publish(BookingEventConfig.EXCHANGE_NAME, "booking.completed", event.bookingId(), event);
	}

	public void publishBookingCancelled(BookingCancelledEvent event) {
		publish(BookingEventConfig.EXCHANGE_NAME, "booking.cancelled", event.bookingId(), event);
	}

	// A private helper to handle the strict logging and MDC requirements
	private void publish(String exchange, String routingKey, Long bookingId, Object payload) {
		try {
			MDC.put("bookingId", String.valueOf(bookingId));
			MDC.put("routingKey", routingKey);

			log.info("Published {} for {}={}", routingKey, "bookingId", bookingId);
			rabbitTemplate.convertAndSend(exchange, routingKey, payload);

		} finally {
			MDC.remove("bookingId");
			MDC.remove("routingKey");
		}
	}
}