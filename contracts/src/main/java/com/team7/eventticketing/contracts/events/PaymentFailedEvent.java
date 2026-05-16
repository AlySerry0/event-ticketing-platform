package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record PaymentFailedEvent(
		Long bookingId,
		String reason,
		LocalDateTime occurredAt
) {
}