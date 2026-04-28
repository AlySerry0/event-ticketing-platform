package com.team7.eventticketing.booking.observer;

public interface EntitySubject {
    void register(EntityObserver o);
    void unregister(EntityObserver o);
    void notifyObservers(String action, Object payload);
}
