package com.team7.eventticketing.user.observer;

/**
 * Classical GoF Observer interface.
 * Each observer receives a notification when an entity state changes.
 * Do NOT use @EventListener — all MongoDB event writes must flow through
 * this classical Observer chain per Section 3.3 of the M2 spec.
 */
public interface EntityObserver {

    /**
     * Called when an entity state change occurs.
     *
     * @param eventType a string describing what happened, e.g. "REGISTERED", "LOGGED_IN"
     * @param payload   the data associated with the event, typically a Map<String, Object>
     */
    void onEvent(String eventType, Object payload);
}