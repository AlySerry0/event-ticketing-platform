package com.team7.eventticketing.event.observer;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Common interface for all MongoDB audit event types.
 * Used by the Factory pattern to return any concrete event typed as MongoEvent.
 */
public interface MongoEvent {
    String getId();
    LocalDateTime getTimestamp();
    String getAction();
    Map<String, Object> getDetails();
}