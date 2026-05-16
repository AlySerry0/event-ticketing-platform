package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record PaymentCompletedEvent(
		Long bookingId,
		LocalDateTime occurredAt
) {
}