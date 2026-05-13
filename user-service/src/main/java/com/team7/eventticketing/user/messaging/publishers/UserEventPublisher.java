package com.team7.eventticketing.user.messaging.publishers;

import com.team7.eventticketing.contracts.events.UserDeactivatedEvent;
import com.team7.eventticketing.contracts.events.UserRegisteredEvent;
import com.team7.eventticketing.user.messaging.config.UserEventConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(
                UserEventConfig.USER_EVENTS_EXCHANGE,
                UserEventConfig.USER_REGISTERED_KEY,
                event
        );
        log.info("Published {} for userId={}", UserEventConfig.USER_REGISTERED_KEY, event.userId());
    }

    public void publishUserDeactivated(UserDeactivatedEvent event) {
        rabbitTemplate.convertAndSend(
                UserEventConfig.USER_EVENTS_EXCHANGE,
                UserEventConfig.USER_DEACTIVATED_KEY,
                event
        );
        log.info("Published {} for userId={}", UserEventConfig.USER_DEACTIVATED_KEY, event.userId());
    }
}