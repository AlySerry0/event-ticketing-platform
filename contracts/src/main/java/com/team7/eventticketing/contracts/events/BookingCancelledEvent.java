package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record BookingCancelledEvent(
        Long bookingId,
        Long userId,
        Long eventId,
        String reason,
        LocalDateTime occurredAt
) {
}