package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record PaymentCompletedEvent(
        Long saleId,
        Long bookingId,
        Double amount,
        LocalDateTime occurredAt
) {
}
