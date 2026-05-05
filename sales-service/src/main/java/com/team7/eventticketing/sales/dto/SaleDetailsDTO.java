package com.team7.eventticketing.sales.dto;

import com.team7.eventticketing.sales.model.PaymentMethod;
import com.team7.eventticketing.sales.model.TicketSaleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class SaleDetailsDTO {

    private Long saleId;
    private Long bookingId;
    private Long userId;
    private Double originalAmount;
    private PaymentMethod method;
    private TicketSaleStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedPromotionDTO> appliedPromotions;
    private Double totalDiscount;
    private Double finalAmount;

    public static class AppliedPromotionDTO {
        private String promotionCode;
        private String discountType;
        private Double discountApplied;
        private LocalDateTime appliedAt;

        public AppliedPromotionDTO(String promotionCode, String discountType,
                                   Double discountApplied, LocalDateTime appliedAt) {
            this.promotionCode = promotionCode;
            this.discountType = discountType;
            this.discountApplied = discountApplied;
            this.appliedAt = appliedAt;
        }

        public String getPromotionCode() { return promotionCode; }
        public String getDiscountType() { return discountType; }
        public Double getDiscountApplied() { return discountApplied; }
        public LocalDateTime getAppliedAt() { return appliedAt; }
    }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(Double originalAmount) { this.originalAmount = originalAmount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public TicketSaleStatus getStatus() { return status; }
    public void setStatus(TicketSaleStatus status) { this.status = status; }

    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public void setTransactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; }

    public List<AppliedPromotionDTO> getAppliedPromotions() { return appliedPromotions; }
    public void setAppliedPromotions(List<AppliedPromotionDTO> appliedPromotions) { this.appliedPromotions = appliedPromotions; }

    public Double getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(Double totalDiscount) { this.totalDiscount = totalDiscount; }

    public Double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(Double finalAmount) { this.finalAmount = finalAmount; }

    // ===== Builder Pattern for Phase 5 =====
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SaleDetailsDTO dto = new SaleDetailsDTO();

        public Builder saleId(Long saleId) {
            dto.saleId = saleId;
            return this;
        }

        public Builder bookingId(Long bookingId) {
            dto.bookingId = bookingId;
            return this;
        }

        public Builder userId(Long userId) {
            dto.userId = userId;
            return this;
        }

        public Builder originalAmount(Double originalAmount) {
            dto.originalAmount = originalAmount;
            return this;
        }

        public Builder method(PaymentMethod method) {
            dto.method = method;
            return this;
        }

        public Builder status(TicketSaleStatus status) {
            dto.status = status;
            return this;
        }

        public Builder transactionDetails(Map<String, Object> transactionDetails) {
            dto.transactionDetails = transactionDetails;
            return this;
        }

        public Builder appliedPromotions(List<AppliedPromotionDTO> appliedPromotions) {
            dto.appliedPromotions = appliedPromotions;
            return this;
        }

        public Builder totalDiscount(Double totalDiscount) {
            dto.totalDiscount = totalDiscount;
            return this;
        }

        public Builder finalAmount(Double finalAmount) {
            dto.finalAmount = finalAmount;
            return this;
        }

        public SaleDetailsDTO build() {
            return dto;
        }
    }
}