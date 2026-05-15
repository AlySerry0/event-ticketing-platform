package com.team7.eventticketing.contracts.events;

public record UserRegisteredEvent(Long userId, String email, String role) {}

