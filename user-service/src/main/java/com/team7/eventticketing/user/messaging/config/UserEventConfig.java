package com.team7.eventticketing.user.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";

    public static final String USER_REGISTERED_KEY = "user.registered";
    public static final String USER_DEACTIVATED_KEY = "user.deactivated";

    public static final String BOOKING_COMPLETED_KEY = "booking.completed";
    public static final String BOOKING_CANCELLED_KEY = "booking.cancelled";

    public static final String SAGA_LISTENER_QUEUE = "user.booking.saga-listener";
    public static final String SAGA_LISTENER_DLX = "user.booking.saga-listener.dlx";
    public static final String SAGA_LISTENER_DLQ = "user.booking.saga-listener.dlq";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange sagaListenerDlx() {
        return new TopicExchange(SAGA_LISTENER_DLX, true, false);
    }

    @Bean
    public Queue sagaListenerQueue() {
        return QueueBuilder.durable(SAGA_LISTENER_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_LISTENER_DLX)
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    @Bean
    public Queue sagaListenerDlq() {
        return QueueBuilder.durable(SAGA_LISTENER_DLQ).build();
    }

    @Bean
    public Binding bindBookingCompleted(Queue sagaListenerQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(sagaListenerQueue).to(bookingEventsExchange).with(BOOKING_COMPLETED_KEY);
    }

    @Bean
    public Binding bindBookingCancelled(Queue sagaListenerQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(sagaListenerQueue).to(bookingEventsExchange).with(BOOKING_CANCELLED_KEY);
    }

    @Bean
    public Binding bindDlq(Queue sagaListenerDlq, TopicExchange sagaListenerDlx) {
        return BindingBuilder.bind(sagaListenerDlq).to(sagaListenerDlx).with("dead");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}