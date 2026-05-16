package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record PaymentRefundedEvent(
		Long bookingId,
		LocalDateTime occurredAt
) {
}