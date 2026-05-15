package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record EventRatedEvent(
        Long eventId,
        Long bookingId,
        Integer rating,
        Double newAverageRating,
        Integer totalRatings,
        LocalDateTime occurredAt
) {
}