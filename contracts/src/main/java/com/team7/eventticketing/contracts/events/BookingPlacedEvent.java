package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record BookingPlacedEvent(
        Long bookingId,
        Long userId,
        Long eventId,
        LocalDateTime occurredAt
) {
}