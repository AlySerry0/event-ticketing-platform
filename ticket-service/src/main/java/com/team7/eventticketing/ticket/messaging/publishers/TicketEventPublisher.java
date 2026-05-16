package com.team7.eventticketing.ticket.messaging.publishers;

import com.team7.eventticketing.contracts.events.TicketCancelledEvent;
import com.team7.eventticketing.contracts.events.TicketIssuedEvent;
import com.team7.eventticketing.contracts.events.TicketStatusChangedEvent;
import com.team7.eventticketing.ticket.config.TicketEventConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTicketIssued(TicketIssuedEvent event) {
        MDC.put("routingKey", TicketEventConfig.ROUTING_KEY_TICKET_ISSUED);
        MDC.put("ticketId", String.valueOf(event.ticketId()));
        MDC.put("bookingId", String.valueOf(event.bookingId()));

        try {
            log.info("Published {} for ticketId={}", TicketEventConfig.ROUTING_KEY_TICKET_ISSUED, event.ticketId());
            rabbitTemplate.convertAndSend(
                    TicketEventConfig.TICKET_EVENTS_EXCHANGE,
                    TicketEventConfig.ROUTING_KEY_TICKET_ISSUED,
                    event);
        } finally {
            MDC.remove("routingKey");
            MDC.remove("ticketId");
            MDC.remove("bookingId");
        }
    }

    public void publishTicketStatusChanged(TicketStatusChangedEvent event) {
        MDC.put("routingKey", TicketEventConfig.ROUTING_KEY_TICKET_STATUS_CHANGED);
        MDC.put("ticketId", String.valueOf(event.ticketId()));
        MDC.put("bookingId", String.valueOf(event.bookingId()));

        try {
            log.info("Published {} for ticketId={}", TicketEventConfig.ROUTING_KEY_TICKET_STATUS_CHANGED, event.ticketId());
            rabbitTemplate.convertAndSend(
                    TicketEventConfig.TICKET_EVENTS_EXCHANGE,
                    TicketEventConfig.ROUTING_KEY_TICKET_STATUS_CHANGED,
                    event);
        } finally {
            MDC.remove("routingKey");
            MDC.remove("ticketId");
            MDC.remove("bookingId");
        }
    }

    public void publishTicketCancelled(TicketCancelledEvent event) {
        MDC.put("routingKey", TicketEventConfig.ROUTING_KEY_TICKET_CANCELLED);
        MDC.put("ticketId", String.valueOf(event.ticketId()));
        MDC.put("bookingId", String.valueOf(event.bookingId()));

        try {
            log.info("Published {} for ticketId={}", TicketEventConfig.ROUTING_KEY_TICKET_CANCELLED, event.ticketId());
            rabbitTemplate.convertAndSend(
                    TicketEventConfig.TICKET_EVENTS_EXCHANGE,
                    TicketEventConfig.ROUTING_KEY_TICKET_CANCELLED,
                    event);
        } finally {
            MDC.remove("routingKey");
            MDC.remove("ticketId");
            MDC.remove("bookingId");
        }
    }
}