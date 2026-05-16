package com.team7.eventticketing.contracts.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingCompletedEvent(
        Long bookingId,
        Long userId,
        Long eventId,
        LocalDateTime occurredAt,
        BigDecimal totalAmount
) {
}