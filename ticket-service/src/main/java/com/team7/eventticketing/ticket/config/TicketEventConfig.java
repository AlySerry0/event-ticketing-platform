package com.team7.eventticketing.ticket.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketEventConfig {

    public static final String TICKET_EVENTS_EXCHANGE = "ticket.events";

    public static final String ROUTING_KEY_TICKET_ISSUED         = "ticket.issued";
    public static final String ROUTING_KEY_TICKET_STATUS_CHANGED = "ticket.status-changed";
    public static final String ROUTING_KEY_TICKET_CANCELLED      = "ticket.cancelled";

    @Bean
    public TopicExchange ticketEventsExchange() {
        return new TopicExchange(TICKET_EVENTS_EXCHANGE, true, false);
    }
}
