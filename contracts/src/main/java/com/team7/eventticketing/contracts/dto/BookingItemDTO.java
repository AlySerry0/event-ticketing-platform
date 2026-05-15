package com.team7.eventticketing.contracts.dto;

import java.util.Map;

public record BookingItemDTO(
        Long id,
        Integer eventOrder,
        Long sessionId,
        String sessionTitle,
        Integer quantity,
        Double unitPrice,
        String status,
        Map<String, Object> metadata
) {}
