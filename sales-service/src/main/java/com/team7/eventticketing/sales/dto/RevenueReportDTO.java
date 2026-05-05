package com.team7.eventticketing.sales.dto;

public class RevenueReportDTO {

    private Double totalRevenue;
    private Long totalTransactions;
    private Double averageSale;
    private Double refundedAmount;
    private Long refundCount;

    private RevenueReportDTO(Builder builder) {
        this.totalRevenue = builder.totalRevenue;
        this.totalTransactions = builder.totalTransactions;
        this.averageSale = builder.averageSale;
        this.refundedAmount = builder.refundedAmount;
        this.refundCount = builder.refundCount;
    }

    public Double getTotalRevenue() { return totalRevenue; }
    public Long getTotalTransactions() { return totalTransactions; }
    public Double getAverageSale() { return averageSale; }
    public Double getRefundedAmount() { return refundedAmount; }
    public Long getRefundCount() { return refundCount; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Double totalRevenue;
        private Long totalTransactions;
        private Double averageSale;
        private Double refundedAmount;
        private Long refundCount;

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder totalTransactions(Long totalTransactions) {
            this.totalTransactions = totalTransactions;
            return this;
        }

        public Builder averageSale(Double averageSale) {
            this.averageSale = averageSale;
            return this;
        }

        public Builder refundedAmount(Double refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Builder refundCount(Long refundCount) {
            this.refundCount = refundCount;
            return this;
        }

        public RevenueReportDTO build() {
            return new RevenueReportDTO(this);
        }
    }
}