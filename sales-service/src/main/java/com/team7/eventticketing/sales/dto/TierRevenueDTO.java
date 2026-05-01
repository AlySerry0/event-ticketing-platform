package com.team7.eventticketing.sales.dto;

public class TierRevenueDTO {

    private String tier;
    private Double totalRevenue;
    private Long saleCount;
    private Long ticketsSold;
    private Double averageRevenuePerSale;

    private TierRevenueDTO() {
    }

    public String getTier() {
        return tier;
    }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public Long getSaleCount() {
        return saleCount;
    }

    public Long getTicketsSold() {
        return ticketsSold;
    }

    public Double getAverageRevenuePerSale() {
        return averageRevenuePerSale;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final TierRevenueDTO dto = new TierRevenueDTO();

        public Builder tier(String tier) {
            dto.tier = tier;
            return this;
        }

        public Builder totalRevenue(Double totalRevenue) {
            dto.totalRevenue = totalRevenue;
            return this;
        }

        public Builder saleCount(Long saleCount) {
            dto.saleCount = saleCount;
            return this;
        }

        public Builder ticketsSold(Long ticketsSold) {
            dto.ticketsSold = ticketsSold;
            return this;
        }

        public Builder averageRevenuePerSale(Double averageRevenuePerSale) {
            dto.averageRevenuePerSale = averageRevenuePerSale;
            return this;
        }

        public TierRevenueDTO build() {
            return dto;
        }
    }
}