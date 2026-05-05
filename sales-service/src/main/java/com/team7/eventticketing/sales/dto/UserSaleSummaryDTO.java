package com.team7.eventticketing.sales.dto;

import java.util.Map;

public class UserSaleSummaryDTO {

    private Long userId;
    private Integer totalSales;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    // Private constructor (only Builder can call it)
    private UserSaleSummaryDTO(Builder builder) {
        this.userId = builder.userId;
        this.totalSales = builder.totalSales;
        this.totalAmount = builder.totalAmount;
        this.methodBreakdown = builder.methodBreakdown;
    }

    // Getters
    public Long getUserId() {
        return userId;
    }

    public Integer getTotalSales() {
        return totalSales;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public Map<String, Double> getMethodBreakdown() {
        return methodBreakdown;
    }

    // Static builder() method
    public static Builder builder() {
        return new Builder();
    }

    // Builder class
    public static class Builder {
        private Long userId;
        private Integer totalSales;
        private Double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder totalSales(Integer totalSales) {
            this.totalSales = totalSales;
            return this;
        }

        public Builder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder methodBreakdown(Map<String, Double> methodBreakdown) {
            this.methodBreakdown = methodBreakdown;
            return this;
        }

        public UserSaleSummaryDTO build() {
            return new UserSaleSummaryDTO(this);
        }
    }
}