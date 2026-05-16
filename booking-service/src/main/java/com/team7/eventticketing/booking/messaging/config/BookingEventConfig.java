package com.team7.eventticketing.booking.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingEventConfig {

	public static final String EXCHANGE_NAME = "booking.events";
	public static final String SAGA_QUEUE = "booking.saga-feedback";
	public static final String SAGA_DLQ = "booking.saga-feedback.dlq";

	// The exchange where booking-service publishes its events
	@Bean
	public TopicExchange bookingEventsExchange() {
		return new TopicExchange(EXCHANGE_NAME);
	}

	// The Dead Letter Queue for failed messages
	@Bean
	public Queue sagaFeedbackDlq() {
		return QueueBuilder.durable(SAGA_DLQ).build();
	}

	// The main queue we listen to, configured to send failures to the DLQ
	@Bean
	public Queue sagaFeedbackQueue() {
		return QueueBuilder.durable(SAGA_QUEUE)
				.withArgument("x-dead-letter-exchange", "") // Default exchange
				.withArgument("x-dead-letter-routing-key", SAGA_DLQ)
				.build();
	}
}