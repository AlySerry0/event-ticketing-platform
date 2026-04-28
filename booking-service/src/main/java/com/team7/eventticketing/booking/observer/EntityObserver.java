package com.team7.eventticketing.booking.observer;

public interface EntityObserver {
    void onEvent(String action, Object payload);
}
