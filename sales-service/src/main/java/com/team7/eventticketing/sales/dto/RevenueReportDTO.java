package com.team7.eventticketing.sales.dto;

public class RevenueReportDTO {

    private Double totalRevenue;
    private Long totalTransactions;
    private Double averageSale;
    private Double refundedAmount;
    private Long refundCount;

    private RevenueReportDTO() {}
    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public Long getTotalTransactions() {
        return totalTransactions;
    }

    public Double getAverageSale() {
        return averageSale;
    }

    public Double getRefundedAmount() {
        return refundedAmount;
    }

    public Long getRefundCount() {
        return refundCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RevenueReportDTO dto = new RevenueReportDTO();

        public Builder totalRevenue(Double totalRevenue) {
            dto.totalRevenue = totalRevenue;
            return this;
        }

        public Builder totalTransactions(Long totalTransactions) {
            dto.totalTransactions = totalTransactions;
            return this;
        }

        public Builder averageSale(Double averageSale) {
            dto.averageSale = averageSale;
            return this;
        }

        public Builder refundedAmount(Double refundedAmount) {
            dto.refundedAmount = refundedAmount;
            return this;
        }

        public Builder refundCount(Long refundCount) {
            dto.refundCount = refundCount;
            return this;
        }

        public RevenueReportDTO build() {
            return dto;
        }
    }
}