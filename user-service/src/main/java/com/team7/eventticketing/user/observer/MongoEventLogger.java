package com.team7.eventticketing.user.observer;

import com.team7.eventticketing.user.model.AuthEvent;
import com.team7.eventticketing.user.repository.AuthEventRepository;
import com.team7.eventticketing.user.util.CacheInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Concrete EntityObserver that persists audit events to MongoDB.
 *
 * Per PDF §4.4.4 NoSQL-writer → cached-reader rule:
 * After persisting a new AuthEvent, invalidate the S1-F12 activity feed cache
 * for the affected user so the new event is visible before the 5-min TTL expires.
 *
 * NOT a Spring bean — manually instantiated by UserService and AuthService
 * (Section 3.3 classical GoF Observer requirement).
 */
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final EventType boundEventType = EventType.AUTH;
    private final AuthEventRepository authEventRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public MongoEventLogger(AuthEventRepository authEventRepository,
                            CacheInvalidationService cacheInvalidationService) {
        this.authEventRepository = authEventRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Override
    public void onEvent(String actionString, Object payload) {
        try {
            Map<String, Object> params = new HashMap<>();

            if (payload instanceof Map<?, ?> payloadMap) {
                payloadMap.forEach((k, v) -> params.put(k.toString(), v));
            }

            params.put("action", actionString);

            if (!params.containsKey("timestamp")) {
                params.put("timestamp", LocalDateTime.now());
            }

            MongoEvent event = EventFactory.createEvent(boundEventType, params);
            authEventRepository.save((AuthEvent) event);

            log.debug("MongoEventLogger persisted event [{}] for userId [{}]",
                    actionString, params.get("userId"));

            // PDF §4.4.4 — invalidate the affected user's S1-F12 cache so the
            // activity feed surfaces this new event immediately.
            Object userIdObj = params.get("userId");
            if (userIdObj != null) {
                cacheInvalidationService.invalidateCacheWildcard(
                        "user-service::S1-F12::" + userIdObj + "::*");
            }

        } catch (Exception e) {
            // CRITICAL: never rethrow — MongoDB is a soft dependency
            log.warn("MongoEventLogger failed to persist event [{}]: {}",
                    actionString, e.getMessage());
        }
    }
}