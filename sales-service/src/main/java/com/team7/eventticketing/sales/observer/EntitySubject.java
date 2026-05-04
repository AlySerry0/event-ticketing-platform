package com.team7.eventticketing.sales.observer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EntitySubject {

    private final List<EntityObserver> observers = new ArrayList<>();

    public EntitySubject(List<EntityObserver> observers) {
        this.observers.addAll(observers);
    }

    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }
}