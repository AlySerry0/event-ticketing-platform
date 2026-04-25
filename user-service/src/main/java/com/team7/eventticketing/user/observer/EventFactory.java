package com.team7.eventticketing.user.observer;

import com.team7.eventticketing.user.model.AuthEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory class that creates the correct MongoEvent subtype based on EventType.
 *
 * This is the Factory design pattern implementation required by Section 3.7.
 * No service class should ever call new AuthEvent(...) directly —
 * all event construction must go through this factory.
 *
 * How it composes with Observer:
 * 1. A write completes in the service
 * 2. Service calls notifyObservers(actionString, payload)
 * 3. MongoEventLogger receives the call
 * 4. Logger builds params map and calls EventFactory.createEvent(AUTH, params)
 * 5. Factory returns the correct MongoEvent subtype
 * 6. Logger persists via Spring Data repository
 */
public class EventFactory {

    /**
     * Creates the appropriate MongoEvent subtype based on the given EventType.
     *
     * Expected keys in params:
     *   - "userId"    Long    the ID of the user involved
     *   - "action"    String  UPPER_SNAKE_CASE action identifier
     *   - "timestamp" LocalDateTime  when the event occurred (defaults to now if absent)
     *   - any other keys are stored in the details map
     *
     * @param type   which concrete event class to create
     * @param params map of data to populate the event with
     * @return the created event typed as MongoEvent
     */
    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case AUTH -> buildAuthEvent(params);

            // Other services handle their own types in their own factories
            default -> throw new IllegalArgumentException(
                    "EventType " + type + " is not handled in user-service EventFactory");
        };
    }

    // -------------------------------------------------------------------------
    // Private builders
    // -------------------------------------------------------------------------

    private static AuthEvent buildAuthEvent(Map<String, Object> params) {
        // Extract known fields
        Long userId = params.get("userId") != null
                ? Long.valueOf(params.get("userId").toString())
                : null;

        String action = (String) params.get("action");

        LocalDateTime timestamp = params.get("timestamp") instanceof LocalDateTime
                ? (LocalDateTime) params.get("timestamp")
                : LocalDateTime.now();

        // Everything else goes into details
        Map<String, Object> details = new HashMap<>(params);
        details.remove("userId");
        details.remove("action");
        details.remove("timestamp");

        return new AuthEvent(userId, action, timestamp, details);
    }
}