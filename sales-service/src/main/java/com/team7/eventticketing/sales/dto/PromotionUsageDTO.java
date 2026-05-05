package com.team7.eventticketing.sales.dto;

public class PromotionUsageDTO {

    private Long promotionId;
    private String code;
    private String discountType;
    private Integer timesUsed;
    private Double discountValue;
    private Double totalDiscountGiven;
    private Boolean active;
    private Boolean expired;

    // Private constructor
    private PromotionUsageDTO(Builder builder) {
        this.promotionId = builder.promotionId;
        this.code = builder.code;
        this.discountType = builder.discountType;
        this.timesUsed = builder.timesUsed;
        this.discountValue = builder.discountValue;
        this.totalDiscountGiven = builder.totalDiscountGiven;
        this.active = builder.active;
        this.expired = builder.expired;
    }

    // Getters only
    public Long getPromotionId() { return promotionId; }
    public String getCode() { return code; }
    public String getDiscountType() { return discountType; }
    public Integer getTimesUsed() { return timesUsed; }
    public Double getDiscountValue() { return discountValue; }
    public Double getTotalDiscountGiven() { return totalDiscountGiven; }
    public Boolean getActive() { return active; }
    public Boolean getExpired() { return expired; }

    // builder() method (IMPORTANT — you missed this before)
    public static Builder builder() {
        return new Builder();
    }

    // Builder class
    public static class Builder {
        private Long promotionId;
        private String code;
        private String discountType;
        private Integer timesUsed;
        private Double discountValue;
        private Double totalDiscountGiven;
        private Boolean active;
        private Boolean expired;

        public Builder promotionId(Long promotionId) {
            this.promotionId = promotionId;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder discountType(String discountType) {
            this.discountType = discountType;
            return this;
        }

        public Builder timesUsed(Integer timesUsed) {
            this.timesUsed = timesUsed;
            return this;
        }

        public Builder discountValue(Double discountValue) {
            this.discountValue = discountValue;
            return this;
        }

        public Builder totalDiscountGiven(Double totalDiscountGiven) {
            this.totalDiscountGiven = totalDiscountGiven;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder expired(Boolean expired) {
            this.expired = expired;
            return this;
        }

        public PromotionUsageDTO build() {
            return new PromotionUsageDTO(this);
        }
    }
}