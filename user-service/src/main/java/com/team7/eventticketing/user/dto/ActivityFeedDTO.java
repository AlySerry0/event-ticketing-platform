package com.team7.eventticketing.user.dto;

import java.util.List;

/**
 * Paginated response wrapper for the user activity feed (S1-F12).
 * Matches the exact response shape required by Section 10.1.3.
 */
public record ActivityFeedDTO(
        List<ActivityEventDTO> content,
        int page,
        int size,
        long totalElements
) {}