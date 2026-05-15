package com.team7.eventticketing.contracts.dto;

import java.time.LocalDateTime;

public record EventDTO(
        Long id,
        String name,
        String venue,
        LocalDateTime eventDate,
        String category,
        String status,
        Double rating
) {
}
