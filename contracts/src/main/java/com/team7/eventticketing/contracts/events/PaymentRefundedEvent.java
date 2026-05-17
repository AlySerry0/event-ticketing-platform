package com.team7.eventticketing.contracts.events;

import java.time.LocalDateTime;

public record PaymentRefundedEvent(
        Long saleId,
        Long bookingId,
        Double refundAmount,
        LocalDateTime occurredAt
) {
}
