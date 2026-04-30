package com.team7.eventticketing.sales.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}