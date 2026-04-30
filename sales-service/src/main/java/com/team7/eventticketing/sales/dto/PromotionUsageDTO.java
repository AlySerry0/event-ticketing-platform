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
    public PromotionUsageDTO(){};

    public Long getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(Long promotionId) {
        this.promotionId = promotionId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(Double discountValue) {
        this.discountValue = discountValue;
    }

    public Integer getTimesUsed() {
        return timesUsed;
    }

    public void setTimesUsed(Integer timesUsed) {
        this.timesUsed = timesUsed;
    }

    public Double getTotalDiscountGiven() {
        return totalDiscountGiven;
    }

    public void setTotalDiscountGiven(Double totalDiscountGiven) {
        this.totalDiscountGiven = totalDiscountGiven;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getExpired() {
        return expired;
    }

    public void setExpired(Boolean expired) {
        this.expired = expired;
    }



    // constructor
    public PromotionUsageDTO(Long promotionId, String code, String discountType,
                             Double discountValue, Integer timesUsed,
                             Double totalDiscountGiven, Boolean active, Boolean expired) {
        this.promotionId = promotionId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.timesUsed = timesUsed;
        this.totalDiscountGiven = totalDiscountGiven;
        this.active = active;
        this.expired = expired;
    }
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
            return new PromotionUsageDTO(
                    promotionId,
                    code,
                    discountType,
                    discountValue,
                    timesUsed,
                    totalDiscountGiven,
                    active,
                    expired
            );
        }
    }
}