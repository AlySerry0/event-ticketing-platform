package com.team7.eventticketing.booking.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {

	public static final String PAYMENT_EXCHANGE = "payment.events";

	// Reference to the sales-service exchange
	@Bean
	public TopicExchange paymentEventsExchange() {
		return new TopicExchange(PAYMENT_EXCHANGE);
	}

	// --- Bindings ---
	// These tell RabbitMQ to route specific payment events into our saga queue

	@Bean
	public Binding bindPaymentInitiated(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
		return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.initiated");
	}

	@Bean
	public Binding bindPaymentCompleted(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
		return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.completed");
	}

	@Bean
	public Binding bindPaymentFailed(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
		return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.failed");
	}

	@Bean
	public Binding bindPaymentRefunded(Queue sagaFeedbackQueue, TopicExchange paymentEventsExchange) {
		return BindingBuilder.bind(sagaFeedbackQueue).to(paymentEventsExchange).with("payment.refunded");
	}
}