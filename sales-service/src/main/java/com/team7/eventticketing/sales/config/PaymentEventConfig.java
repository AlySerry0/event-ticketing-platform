package com.team7.eventticketing.sales.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {

    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String BOOKING_EXCHANGE = "booking.events";

    public static final String PAYMENT_SAGA_QUEUE = "payment.saga-listener";
    public static final String PAYMENT_SAGA_DLQ = "payment.saga-listener.dlq";
    public static final String PAYMENT_SAGA_DLX = "payment.saga-listener.dlx";

    public static final String BOOKING_COMPLETED_ROUTING_KEY = "booking.completed";
    public static final String BOOKING_CANCELLED_ROUTING_KEY = "booking.cancelled";

    public static final String PAYMENT_INITIATED_ROUTING_KEY = "payment.initiated";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_REFUNDED_ROUTING_KEY = "payment.refunded";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentSagaDeadLetterExchange() {
        return new TopicExchange(PAYMENT_SAGA_DLX, true, false);
    }

    @Bean
    public Queue paymentSagaQueue() {
        return QueueBuilder.durable(PAYMENT_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_SAGA_DLX)
                .withArgument("x-dead-letter-routing-key", PAYMENT_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue paymentSagaDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_SAGA_DLQ).build();
    }

    @Bean
    public Binding bookingCompletedBinding(
            Queue paymentSagaQueue,
            TopicExchange bookingExchange
    ) {
        return BindingBuilder.bind(paymentSagaQueue)
                .to(bookingExchange)
                .with(BOOKING_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding bookingCancelledBinding(
            Queue paymentSagaQueue,
            TopicExchange bookingExchange
    ) {
        return BindingBuilder.bind(paymentSagaQueue)
                .to(bookingExchange)
                .with(BOOKING_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentSagaDlqBinding(
            Queue paymentSagaDeadLetterQueue,
            TopicExchange paymentSagaDeadLetterExchange
    ) {
        return BindingBuilder.bind(paymentSagaDeadLetterQueue)
                .to(paymentSagaDeadLetterExchange)
                .with(PAYMENT_SAGA_DLQ);
    }
}