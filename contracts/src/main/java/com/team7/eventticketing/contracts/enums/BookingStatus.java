package com.team7.eventticketing.contracts.enums;

public enum BookingStatus {
    PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, // M1 Statuses
    COMPLETING, PAYMENT_PENDING, PAID, PAYMENT_FAILED, REFUNDED // New M3 Statuses
}