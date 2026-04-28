package com.team7.eventticketing.ticket.observer;

public interface EntityObserver {
    void onEvent(String action, Object payload);
}
