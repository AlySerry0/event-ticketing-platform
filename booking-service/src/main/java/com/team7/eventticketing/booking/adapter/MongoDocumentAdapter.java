package com.team7.eventticketing.booking.adapter;

import com.team7.eventticketing.booking.observer.BookingEvent;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    public BookingEvent adapt(Document source) {
        Long bookingId = source.containsKey("bookingId")
                ? ((Number) source.get("bookingId")).longValue()
                : null;
        String action = source.getString("action");
        Object ts = source.get("timestamp");
        LocalDateTime timestamp = ts instanceof LocalDateTime ldt ? ldt : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> details = source.containsKey("details")
                ? (Map<String, Object>) source.get("details")
                : null;

        return new BookingEvent(bookingId, action, timestamp, details);
    }
}
