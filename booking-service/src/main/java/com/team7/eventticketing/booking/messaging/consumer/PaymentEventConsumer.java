package com.team7.eventticketing.booking.messaging.consumer;

import com.team7.eventticketing.booking.messaging.config.BookingEventConfig;
import com.team7.eventticketing.booking.messaging.publisher.BookingEventPublisher;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import com.team7.eventticketing.contracts.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class PaymentEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
	private final BookingRepository bookingRepository;
	private final BookingEventPublisher bookingEventPublisher;

	public PaymentEventConsumer(BookingRepository bookingRepository, BookingEventPublisher bookingEventPublisher) {
		this.bookingRepository = bookingRepository;
		this.bookingEventPublisher = bookingEventPublisher;
	}

	@RabbitListener(queues = BookingEventConfig.SAGA_QUEUE)
	@Transactional
	public void handlePaymentEvents(Object payload, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		try {
			// In a real scenario, you'd extract the bookingId from the specific payload type.
			// For this skeleton, let's assume we extracted it into a variable called `bookingId`.
			Long bookingId = extractBookingId(payload);

			try {
				MDC.put("bookingId", String.valueOf(bookingId));
				MDC.put("routingKey", routingKey);
				log.info("Consuming {} for {}={}", routingKey, "bookingId", bookingId);

				Booking booking = bookingRepository.findById(bookingId).orElseThrow();

				// 1. PAYMENT INITIATED
				if (routingKey.equals("payment.initiated") && booking.getStatus() == BookingStatus.COMPLETING) {
					log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.PAYMENT_PENDING);
					booking.setStatus(BookingStatus.PAYMENT_PENDING);
				}

				// 2. PAYMENT COMPLETED
				else if (routingKey.equals("payment.completed") && booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
					log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.PAID);
					booking.setStatus(BookingStatus.PAID);
				}

				// 3. PAYMENT FAILED (The Compensation Trigger)
				else if (routingKey.equals("payment.failed") && booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
					log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.PAYMENT_FAILED);
					booking.setStatus(BookingStatus.PAYMENT_FAILED);

					// Trigger the compensation cascade!
					bookingEventPublisher.publishBookingCancelled(
							new BookingCancelledEvent(booking.getId(), booking.getUserId(), booking.getEventId(), "payment_failed", LocalDateTime.now())
					);
				}

				// 4. PAYMENT REFUNDED
				else if (routingKey.equals("payment.refunded") && (booking.getStatus() == BookingStatus.PAID || booking.getStatus() == BookingStatus.PAYMENT_FAILED)) {
					log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.REFUNDED);
					booking.setStatus(BookingStatus.REFUNDED);
				}

				bookingRepository.save(booking);
				log.info("Processed {} for {}={}", routingKey, "bookingId", bookingId);

			} finally {
				MDC.remove("bookingId");
				MDC.remove("routingKey");
			}
		} catch (Exception e) {
			log.error("Saga processing failure for routingKey={}: {}", routingKey, e.getMessage(), e);
			throw e; // Re-throw to ensure RabbitMQ retry/DLQ if configured
		}
	}
	private Long extractBookingId(Object payload) {
		return switch (payload) {
			case PaymentInitiatedEvent e -> e.bookingId();
			case PaymentCompletedEvent e -> e.bookingId();
			case PaymentFailedEvent e -> e.bookingId();
			case PaymentRefundedEvent e -> e.bookingId();
			default -> {
				log.error("Received unknown payload type in saga queue: {}", payload.getClass());
				throw new IllegalArgumentException("Unknown event payload");
			}
		};
	}
}