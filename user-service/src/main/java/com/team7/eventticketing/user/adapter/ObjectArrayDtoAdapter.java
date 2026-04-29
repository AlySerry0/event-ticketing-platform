package com.team7.eventticketing.user.adapter;

import com.team7.eventticketing.user.dto.TopAttendeeDTO;
import com.team7.eventticketing.user.dto.UserBookingSummaryDTO;

/**
 * Adapter Pattern (PDF §3.8) — converts native SQL {@code Object[]} rows into typed DTOs.
 *
 * S1 usage:
 *   - S1-F3 user booking summary  (Object[] → UserBookingSummaryDTO)
 *   - S1-F6 top attendees report  (Object[] → TopAttendeeDTO)
 *
 * Pattern:
 *   ObjectArrayDtoAdapter adapter = new ObjectArrayDtoAdapter();
 *   UserBookingSummaryDTO dto = adapter.toUserBookingSummaryDTO(row);
 */
public class ObjectArrayDtoAdapter {

    /**
     * Converts a native-SQL row from {@code UserRepository.getUserBookingSummary}
     * into a {@link UserBookingSummaryDTO}.
     *
     * Expected row order:
     *   [0] userId, [1] name, [2] totalBookings, [3] completedBookings,
     *   [4] cancelledBookings, [5] totalSpent, [6] averageBookingAmount
     */
    public UserBookingSummaryDTO toUserBookingSummaryDTO(Object[] row) {
        UserBookingSummaryDTO dto = new UserBookingSummaryDTO();
        dto.setUserId(((Number) row[0]).longValue());
        dto.setName((String) row[1]);
        dto.setTotalBookings(((Number) row[2]).longValue());
        dto.setCompletedBookings(((Number) row[3]).longValue());
        dto.setCancelledBookings(((Number) row[4]).longValue());
        dto.setTotalSpent(((Number) row[5]).doubleValue());
        dto.setAverageBookingAmount(((Number) row[6]).doubleValue());
        return dto;
    }

    /**
     * Converts a native-SQL row from {@code UserRepository.findTopAttendeesBySpending}
     * into a {@link TopAttendeeDTO}.
     *
     * Expected row order:
     *   [0] userId, [1] name, [2] totalSpent, [3] bookingCount
     */
    public TopAttendeeDTO toTopAttendeeDTO(Object[] row) {
        return new TopAttendeeDTO(
                ((Number) row[0]).longValue(),
                (String)  row[1],
                ((Number) row[2]).doubleValue(),
                ((Number) row[3]).longValue()
        );
    }
}