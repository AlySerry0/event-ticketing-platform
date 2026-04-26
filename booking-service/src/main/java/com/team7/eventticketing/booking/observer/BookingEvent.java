package com.team7.eventticketing.booking.observer;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "booking_events")
public class BookingEvent implements MongoEvent {
    @Id
    private String id;
    private Long bookingId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public BookingEvent() {
    }

    public BookingEvent(Long bookingId, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.bookingId = bookingId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    @Override
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
