package com.team7.eventticketing.sales.strategy;

public class RefundResult {

    private final double refundAmount;
    private final boolean approved;
    private final String reasonCode;
    private final String strategyName;

    public RefundResult(double refundAmount, boolean approved, String reasonCode, String strategyName) {
        this.refundAmount = refundAmount;
        this.approved = approved;
        this.reasonCode = reasonCode;
        this.strategyName = strategyName;
    }

    public double getRefundAmount() {
        return refundAmount;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getStrategyName() {
        return strategyName;
    }
}