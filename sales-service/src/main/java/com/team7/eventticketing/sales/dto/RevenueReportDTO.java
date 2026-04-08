package com.team7.eventticketing.sales.dto;

public class RevenueReportDTO {

    private Double totalRevenue;
    private Long totalTransactions;
    private Double averageSale;
    private Double refundedAmount;
    private Long refundCount;

    public RevenueReportDTO(Double totalRevenue,
                            Long totalTransactions,
                            Double averageSale,
                            Double refundedAmount,
                            Long refundCount) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averageSale = averageSale;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    // Getters
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
}
