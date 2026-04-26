package com.team7.eventticketing.event.observer;

/**
 * Enum of MongoDB event types used by EventFactory to dispatch
 * to the correct concrete MongoEvent subtype.
 *
 * Each service is bound to one EventType at construction time:
 *   event-service → EVENT_ACTIVITY
 */
public enum EventType {
    AUTH,
    EVENT_ACTIVITY,
    BOOKING,
    TICKET,
    PAYMENT_AUDIT
}