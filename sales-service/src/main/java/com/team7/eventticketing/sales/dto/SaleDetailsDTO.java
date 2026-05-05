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

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setOriginalAmount(Double originalAmount) {
        this.originalAmount = originalAmount;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public void setStatus(TicketSaleStatus status) {
        this.status = status;
    }

    public void setTransactionDetails(Map<String, Object> transactionDetails) {
        this.transactionDetails = transactionDetails;
    }

    public void setAppliedPromotions(List<AppliedPromotionDTO> appliedPromotions) {
        this.appliedPromotions = appliedPromotions;
    }

    public void setTotalDiscount(Double totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public void setFinalAmount(Double finalAmount) {
        this.finalAmount = finalAmount;
    }

    private PaymentMethod method;
    private TicketSaleStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedPromotionDTO> appliedPromotions;
    private Double totalDiscount;
    private Double finalAmount;

    private SaleDetailsDTO(Builder builder) {
        this.saleId = builder.saleId;
        this.bookingId = builder.bookingId;
        this.userId = builder.userId;
        this.originalAmount = builder.originalAmount;
        this.method = builder.method;
        this.status = builder.status;
        this.transactionDetails = builder.transactionDetails;
        this.appliedPromotions = builder.appliedPromotions;
        this.totalDiscount = builder.totalDiscount;
        this.finalAmount = builder.finalAmount;
    }

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
    public Long getBookingId() { return bookingId; }
    public Long getUserId() { return userId; }
    public Double getOriginalAmount() { return originalAmount; }
    public PaymentMethod getMethod() { return method; }
    public TicketSaleStatus getStatus() { return status; }
    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public List<AppliedPromotionDTO> getAppliedPromotions() { return appliedPromotions; }
    public Double getTotalDiscount() { return totalDiscount; }
    public Double getFinalAmount() { return finalAmount; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
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

        public Builder saleId(Long saleId) {
            this.saleId = saleId;
            return this;
        }

        public Builder bookingId(Long bookingId) {
            this.bookingId = bookingId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder originalAmount(Double originalAmount) {
            this.originalAmount = originalAmount;
            return this;
        }

        public Builder method(PaymentMethod method) {
            this.method = method;
            return this;
        }

        public Builder status(TicketSaleStatus status) {
            this.status = status;
            return this;
        }

        public Builder transactionDetails(Map<String, Object> transactionDetails) {
            this.transactionDetails = transactionDetails;
            return this;
        }

        public Builder appliedPromotions(List<AppliedPromotionDTO> appliedPromotions) {
            this.appliedPromotions = appliedPromotions;
            return this;
        }

        public Builder totalDiscount(Double totalDiscount) {
            this.totalDiscount = totalDiscount;
            return this;
        }

        public Builder finalAmount(Double finalAmount) {
            this.finalAmount = finalAmount;
            return this;
        }

        public SaleDetailsDTO build() {
            return new SaleDetailsDTO(this);
        }
    }
}