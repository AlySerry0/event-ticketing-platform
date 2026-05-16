package com.team7.eventticketing.contracts.dto;

import com.team7.eventticketing.contracts.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BookingDTO(
        Long id,
        Long userId,
        Long eventId,
        String contactEmail,
        BookingStatus status,
        BigDecimal totalAmount,
        Map<String, Object> metadata,
        LocalDateTime bookingDate,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt,
        List<BookingItemDTO> bookingItems
) {}
