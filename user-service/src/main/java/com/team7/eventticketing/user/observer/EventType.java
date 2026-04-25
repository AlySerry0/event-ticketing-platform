package com.team7.eventticketing.user.observer;

/**
 * Enum representing the type of MongoDB event document to create.
 * Used by EventFactory to dispatch to the correct concrete class.
 *
 * Each service binds to one EventType at construction time:
 * user-service     → AUTH
 * event-service    → EVENT_ACTIVITY
 * booking-service  → BOOKING
 * ticket-service   → TICKET
 * sales-service    → PAYMENT_AUDIT
 */
public enum EventType {
    AUTH,
    EVENT_ACTIVITY,
    BOOKING,
    TICKET,
    PAYMENT_AUDIT
}