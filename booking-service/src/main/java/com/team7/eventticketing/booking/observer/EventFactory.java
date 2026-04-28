package com.team7.eventticketing.booking.observer;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType eventType, Map<String, Object> params) {
        if (EventType.BOOKING.equals(eventType)) {
            Long bookingId = null;
            if (params.containsKey("bookingId")) {
                Object val = params.get("bookingId");
                if (val instanceof Number) {
                    bookingId = ((Number) val).longValue();
                } else if (val != null) {
                    try {
                        bookingId = Long.parseLong(val.toString());
                    } catch (NumberFormatException ignored) {}
                }
            }
            String action = (String) params.getOrDefault("action", "UNKNOWN");
            return new BookingEvent(bookingId, action, LocalDateTime.now(), params);
        }
        return null;
    }
}
