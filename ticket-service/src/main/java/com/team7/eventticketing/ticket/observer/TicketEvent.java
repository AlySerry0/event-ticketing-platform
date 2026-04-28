package com.team7.eventticketing.ticket.observer;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "ticket_events")
public class TicketEvent implements MongoEvent {
    @Id
    private String id;
    private Long ticketId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public TicketEvent() {
    }

    public TicketEvent(Long ticketId, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.ticketId = ticketId;
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

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
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
