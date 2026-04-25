package com.team7.eventticketing.user.observer;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Common interface for all MongoDB audit event documents.
 * All 5 concrete event classes (AuthEvent, EventActivityEvent,
 * BookingEvent, TicketEvent, PaymentAuditEvent) implement this interface.
 *
 * Required by Section 3.7 (Factory Pattern) and Section 7.1.1 of the M2 spec.
 * The EventFactory returns any concrete event typed as MongoEvent.
 */
public interface MongoEvent {

    /**
     * Returns the MongoDB ObjectId as a String.
     * Maps to the @Id field in each concrete document class.
     */
    String getId();

    /**
     * Returns when the event occurred.
     */
    LocalDateTime getTimestamp();

    /**
     * Returns the action identifier e.g. "REGISTERED", "LOGGED_IN", "ROLE_CHANGED".
     * Always UPPER_SNAKE_CASE per spec Section 7.1.
     */
    String getAction();

    /**
     * Returns additional event context as a flexible map.
     * Contents vary per event type and action.
     */
    Map<String, Object> getDetails();
}