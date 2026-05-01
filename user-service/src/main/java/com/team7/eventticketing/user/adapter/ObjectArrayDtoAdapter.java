package com.team7.eventticketing.user.adapter;

import com.team7.eventticketing.user.dto.TopAttendeeDTO;
import com.team7.eventticketing.user.dto.UserBookingSummaryDTO;

/**
 * Adapter Pattern (PDF §3.8) — converts native SQL {@code Object[]} rows into typed DTOs.
 */
public class ObjectArrayDtoAdapter {

    /**
     * Converts a native-SQL row into a {@link UserBookingSummaryDTO} using the Builder pattern.
     *
     * Expected row order:
     *   [0] userId, [1] name, [2] totalBookings, [3] completedBookings,
     *   [4] cancelledBookings, [5] totalSpent, [6] averageBookingAmount
     */
    public UserBookingSummaryDTO toUserBookingSummaryDTO(Object[] row) {
        return UserBookingSummaryDTO.builder()
                .userId(((Number) row[0]).longValue())
                .name((String) row[1])
                .totalBookings(((Number) row[2]).longValue())
                .completedBookings(((Number) row[3]).longValue())
                .cancelledBookings(((Number) row[4]).longValue())
                .totalSpent(((Number) row[5]).doubleValue())
                .averageBookingAmount(((Number) row[6]).doubleValue())
                .build();
    }

    /**
     * Converts a native-SQL row into a {@link TopAttendeeDTO} using the Builder pattern.
     *
     * Expected row order:
     *   [0] userId, [1] name, [2] totalSpent, [3] bookingCount
     */
    public TopAttendeeDTO toTopAttendeeDTO(Object[] row) {
        return TopAttendeeDTO.builder()
                .userId(((Number) row[0]).longValue())
                .name((String) row[1])
                .totalSpent(((Number) row[2]).doubleValue())
                .bookingCount(((Number) row[3]).longValue())
                .build();
    }
}