package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record EventStatusChangedEvent(
        Long eventId,
        String eventName,
        String oldStatus,
        String newStatus,
        LocalDateTime occurredAt
) {
}