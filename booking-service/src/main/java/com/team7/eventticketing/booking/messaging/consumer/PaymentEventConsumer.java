package com.team7.eventticketing.booking.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team7.eventticketing.booking.messaging.config.BookingEventConfig;
import com.team7.eventticketing.booking.messaging.publisher.BookingEventPublisher;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import com.team7.eventticketing.contracts.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class PaymentEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
	private final BookingRepository bookingRepository;
	private final BookingEventPublisher bookingEventPublisher;
	private final ObjectMapper objectMapper;
	private final CacheManager cacheManager;

	public PaymentEventConsumer(BookingRepository bookingRepository,
	                            BookingEventPublisher bookingEventPublisher,
	                            ObjectMapper objectMapper,
	                            CacheManager cacheManager) {
		this.bookingRepository = bookingRepository;
		this.bookingEventPublisher = bookingEventPublisher;
		this.objectMapper = objectMapper;
		this.cacheManager = cacheManager;
	}

	@RabbitListener(queues = BookingEventConfig.SAGA_QUEUE)
	@Transactional
	public void handlePaymentEvents(
			Message message,
			@Header("amqp_receivedRoutingKey") String routingKey
	) throws Exception {

		try {
			if ("payment.initiated".equals(routingKey)) {
				PaymentInitiatedEvent event =
						objectMapper.readValue(message.getBody(), PaymentInitiatedEvent.class);
				handlePaymentInitiated(event);
				return;
			}

			if ("payment.completed".equals(routingKey)) {
				PaymentCompletedEvent event =
						objectMapper.readValue(message.getBody(), PaymentCompletedEvent.class);
				handlePaymentCompleted(event);
				return;
			}

			if ("payment.failed".equals(routingKey)) {
				PaymentFailedEvent event =
						objectMapper.readValue(message.getBody(), PaymentFailedEvent.class);
				handlePaymentFailed(event);
				return;
			}

			if ("payment.refunded".equals(routingKey)) {
				PaymentRefundedEvent event =
						objectMapper.readValue(message.getBody(), PaymentRefundedEvent.class);
				handlePaymentRefunded(event);
				return;
			}

			log.warn("Unrecognised payment routing key={}", routingKey);

		} catch (Exception e) {
			log.error("Saga processing failure for routingKey={}: {}", routingKey, e.getMessage(), e);
			throw e;
		}
	}

	private void handlePaymentInitiated(PaymentInitiatedEvent event) {
		Long bookingId = event.bookingId();
		MDC.put("bookingId", String.valueOf(bookingId));
		MDC.put("routingKey", "payment.initiated");
		try {
			log.info("Consuming payment.initiated for bookingId={}", bookingId);
			Booking booking = bookingRepository.findById(bookingId).orElseThrow();
			if (booking.getStatus() == BookingStatus.COMPLETING) {
				log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.PAYMENT_PENDING);
				booking.setStatus(BookingStatus.PAYMENT_PENDING);
				bookingRepository.save(booking);
				evictBookingCache(bookingId);
				log.info("Processed payment.initiated for bookingId={}", bookingId);
			} else {
				log.warn("payment.initiated ignored: booking {} is in status {}", bookingId, booking.getStatus());
			}
		} finally {
			MDC.remove("bookingId");
			MDC.remove("routingKey");
		}
	}

	private void handlePaymentCompleted(PaymentCompletedEvent event) {
		Long bookingId = event.bookingId();
		MDC.put("bookingId", String.valueOf(bookingId));
		MDC.put("routingKey", "payment.completed");
		try {
			log.info("Consuming payment.completed for bookingId={}", bookingId);
			Booking booking = bookingRepository.findById(bookingId).orElseThrow();
			if (booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
				log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.PAID);
				booking.setStatus(BookingStatus.PAID);
				bookingRepository.save(booking);
				evictBookingCache(bookingId);
				log.info("Processed payment.completed for bookingId={}", bookingId);
			} else {
				log.warn("payment.completed ignored: booking {} is in status {}", bookingId, booking.getStatus());
			}
		} finally {
			MDC.remove("bookingId");
			MDC.remove("routingKey");
		}
	}

	private void handlePaymentFailed(PaymentFailedEvent event) {
		Long bookingId = event.bookingId();
		MDC.put("bookingId", String.valueOf(bookingId));
		MDC.put("routingKey", "payment.failed");
		try {
			log.info("Consuming payment.failed for bookingId={}", bookingId);
			Booking booking = bookingRepository.findById(bookingId).orElseThrow();
			if (booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
				log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.PAYMENT_FAILED);
				booking.setStatus(BookingStatus.PAYMENT_FAILED);
				bookingRepository.save(booking);
				evictBookingCache(bookingId);
				bookingEventPublisher.publishBookingCancelled(
						new BookingCancelledEvent(booking.getId(), booking.getUserId(),
								booking.getEventId(), "payment_failed", LocalDateTime.now())
				);
				log.info("Processed payment.failed for bookingId={}", bookingId);
			} else {
				log.warn("payment.failed ignored: booking {} is in status {}", bookingId, booking.getStatus());
			}
		} finally {
			MDC.remove("bookingId");
			MDC.remove("routingKey");
		}
	}

	private void handlePaymentRefunded(PaymentRefundedEvent event) {
		Long bookingId = event.bookingId();
		MDC.put("bookingId", String.valueOf(bookingId));
		MDC.put("routingKey", "payment.refunded");
		try {
			log.info("Consuming payment.refunded for bookingId={}", bookingId);
			Booking booking = bookingRepository.findById(bookingId).orElseThrow();
			if (booking.getStatus() == BookingStatus.PAID || booking.getStatus() == BookingStatus.PAYMENT_FAILED) {
				log.info("Booking {} transitioning {} -> {}", bookingId, booking.getStatus(), BookingStatus.REFUNDED);
				booking.setStatus(BookingStatus.REFUNDED);
				bookingRepository.save(booking);
				evictBookingCache(bookingId);
				log.info("Processed payment.refunded for bookingId={}", bookingId);
			} else {
				log.warn("payment.refunded ignored: booking {} is in status {}", bookingId, booking.getStatus());
			}
		} finally {
			MDC.remove("bookingId");
			MDC.remove("routingKey");
		}
	}

	private void evictBookingCache(Long bookingId) {
		var cache = cacheManager.getCache("booking");
		if (cache != null) {
			cache.evict(bookingId);
		}
	}
}
