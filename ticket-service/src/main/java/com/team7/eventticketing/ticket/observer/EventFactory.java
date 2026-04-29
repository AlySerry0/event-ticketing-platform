package com.team7.eventticketing.ticket.observer;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType eventType, Map<String, Object> params) {
        if (EventType.TICKET.equals(eventType)) {
            Long ticketId = null;
            if (params.containsKey("ticketId")) {
                Object val = params.get("ticketId");
                if (val instanceof Number) {
                    ticketId = ((Number) val).longValue();
                } else if (val != null) {
                    try {
                        ticketId = Long.parseLong(val.toString());
                    } catch (NumberFormatException ignored) {}
                }
            }
            String action = (String) params.getOrDefault("action", "UNKNOWN");
            return new TicketEvent(ticketId, action, LocalDateTime.now(), params);
        }
        return null;
    }
}
