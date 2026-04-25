package com.team7.eventticketing.user.model;

import com.team7.eventticketing.user.observer.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB document representing an authentication-related audit event.
 * Stored in the auth_events collection.
 *
 * Implements MongoEvent so EventFactory can return it typed as MongoEvent.
 * Must be a class, not a record, per Section 7.1 of the M2 spec.
 *
 * Action values: REGISTERED, LOGGED_IN, ROLE_CHANGED, USER_UPDATED,
 * USER_DEACTIVATED, DEFAULT_VENUE_SET, USER_CREATED, USER_DELETED
 */
@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {

    @Id
    private String id;

    private Long userId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    // Required no-arg constructor for MongoDB deserialization
    public AuthEvent() {}

    public AuthEvent(Long userId, String action,
                     LocalDateTime timestamp, Map<String, Object> details) {
        this.userId = userId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    // MongoEvent interface implementations
    @Override
    public String getId() { return id; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String getAction() { return action; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    // AuthEvent specific getter
    public Long getUserId() { return userId; }

    // Setters required for MongoDB
    public void setId(String id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}