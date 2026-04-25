package com.team7.eventticketing.user.observer;

import com.team7.eventticketing.user.model.AuthEvent;
import com.team7.eventticketing.user.repository.AuthEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Concrete implementation of EntityObserver that persists audit events to MongoDB.
 *
 * This is the Observer pattern implementation required by Section 3.3.
 * It is NOT a Spring bean — it is instantiated manually by UserService
 * and registered via registerObserver().
 *
 * Failure policy (Section 3.3):
 * Any MongoDB exception is caught, logged at WARN level, and NOT rethrown.
 * The upstream PostgreSQL transaction must never be rolled back due to
 * a MongoDB write failure — MongoDB is a soft dependency.
 *
 * How this composes with Factory (Section 3.7):
 * 1. UserService calls notifyObservers(actionString, payload)
 * 2. This observer receives onEvent(actionString, payload)
 * 3. Builds params map with action + payload data
 * 4. Calls EventFactory.createEvent(AUTH, params) to get the right subtype
 * 5. Persists the result via AuthEventRepository
 *
 * This logger is bound to EventType.AUTH at construction time.
 * It does not infer the EventType from the action string.
 */
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    // Bound at construction — user-service always logs AUTH events
    private final EventType boundEventType = EventType.AUTH;
    private final AuthEventRepository authEventRepository;

    public MongoEventLogger(AuthEventRepository authEventRepository) {
        this.authEventRepository = authEventRepository;
    }

    /**
     * Receives a state-change notification and persists it to MongoDB.
     *
     * @param actionString UPPER_SNAKE_CASE description of what happened
     *                     e.g. "REGISTERED", "LOGGED_IN", "ROLE_CHANGED"
     * @param payload      the data associated with the event,
     *                     expected to be a Map<String, Object> containing at minimum userId
     */
    @Override
    public void onEvent(String actionString, Object payload) {
        try {
            // Build the params map for the factory
            Map<String, Object> params = new HashMap<>();

            // Merge payload into params if it is a map
            if (payload instanceof Map<?, ?> payloadMap) {
                payloadMap.forEach((k, v) -> params.put(k.toString(), v));
            }

            // Action string is required by the factory
            params.put("action", actionString);

            // Default timestamp to now if not provided by the caller
            if (!params.containsKey("timestamp")) {
                params.put("timestamp", LocalDateTime.now());
            }

            // Ask the factory for the correct MongoEvent subtype
            MongoEvent event = EventFactory.createEvent(boundEventType, params);

            // Persist to MongoDB via Spring Data
            authEventRepository.save((AuthEvent) event);

            log.debug("MongoEventLogger persisted event [{}] for userId [{}]",
                    actionString, params.get("userId"));

        } catch (Exception e) {
            // CRITICAL: never rethrow — MongoDB is a soft dependency
            // The upstream PostgreSQL transaction must not be affected
            log.warn("MongoEventLogger failed to persist event [{}]: {}",
                    actionString, e.getMessage());
        }
    }
}