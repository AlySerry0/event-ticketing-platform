package com.team7.eventticketing.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a single event in the user's activity feed.
 * Maps directly from a MongoDB AuthEvent document.
 */
public record ActivityEventDTO(
        String action,
        LocalDateTime timestamp,
        Map<String, Object> details
) {}