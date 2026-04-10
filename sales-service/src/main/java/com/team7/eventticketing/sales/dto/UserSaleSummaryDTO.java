package com.team7.eventticketing.sales.dto;

import java.util.Map;

public record UserSaleSummaryDTO(
        Long userId,
        Integer totalSales,
        Double totalAmount,
        Map<String, Double> methodBreakdown
) {
}