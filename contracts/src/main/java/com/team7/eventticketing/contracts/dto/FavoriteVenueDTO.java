package com.team7.eventticketing.contracts.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record FavoriteVenueDTO(
        Long id,
        String label,
        String venueName,
        String location,
        Integer capacity,
        Boolean isDefault,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {}
