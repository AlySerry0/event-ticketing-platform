package com.team7.eventticketing.event.observer;

/**
 * Observer Pattern — EntityObserver interface.
 *
 * Implemented by any class that wants to react to entity state changes.
 * The event-service registers MongoEventLogger as its concrete observer.
 *
 * Classical GoF pattern — NOT Spring's ApplicationEventPublisher/@EventListener.
 * No method annotated with @EventListener may write to MongoDB.
 */
public interface EntityObserver {

    /**
     * Called by the subject (service) when an entity state change occurs.
     *
     * @param eventType the action identifier, e.g. "EVENT_CREATED", "STATUS_CHANGED"
     * @param payload   the entity or DTO that changed; the observer casts as needed
     */
    void onEvent(String eventType, Object payload);
}