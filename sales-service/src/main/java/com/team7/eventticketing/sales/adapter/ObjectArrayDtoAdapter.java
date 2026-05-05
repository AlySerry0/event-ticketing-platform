package com.team7.eventticketing.sales.adapter;

import com.team7.eventticketing.sales.dto.PromotionUsageDTO;
import com.team7.eventticketing.sales.dto.RevenueReportDTO;
import com.team7.eventticketing.sales.dto.SaleDetailsDTO;
import com.team7.eventticketing.sales.dto.TierRevenueDTO;
import org.springframework.stereotype.Component;
import com.team7.eventticketing.sales.model.TicketSale;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ObjectArrayDtoAdapter {

    public PromotionUsageDTO toPromotionUsageDTO(Object[] row) {

        Long id = ((Number) row[0]).longValue();
        String code = row[1] != null ? row[1].toString() : null;
        String discountType = row[2] != null ? row[2].toString() : null;
        Double discountValue = ((Number) row[3]).doubleValue();
        Integer timesUsed = ((Number) row[4]).intValue();
        Double totalDiscountGiven = ((Number) row[5]).doubleValue();
        Boolean active = (Boolean) row[6];

        LocalDateTime expiryDate = null;
        if (row[7] != null) {
            if (row[7] instanceof java.sql.Timestamp ts) {
                expiryDate = ts.toLocalDateTime();
            } else if (row[7] instanceof LocalDateTime ldt) {
                expiryDate = ldt;
            }
        }

        Boolean expired = expiryDate != null && expiryDate.isBefore(LocalDateTime.now());

        return PromotionUsageDTO.builder()
                .promotionId(id)
                .code(code)
                .discountType(discountType)
                .discountValue(discountValue)
                .timesUsed(timesUsed)
                .totalDiscountGiven(totalDiscountGiven)
                .active(active)
                .expired(expired)
                .build();
    }
    public SaleDetailsDTO toSaleDetailsDTO(TicketSale sale) {
        List<SaleDetailsDTO.AppliedPromotionDTO> appliedPromotions = sale.getSalePromotions()
                .stream()
                .map(sp -> new SaleDetailsDTO.AppliedPromotionDTO(
                        sp.getPromotion().getCode(),
                        sp.getPromotion().getDiscountType().name(),
                        sp.getDiscountApplied(),
                        sp.getAppliedAt()
                ))
                .toList();

        double totalDiscount = appliedPromotions.stream()
                .mapToDouble(SaleDetailsDTO.AppliedPromotionDTO::getDiscountApplied)
                .sum();

        return SaleDetailsDTO.builder()
                .saleId(sale.getId())
                .bookingId(sale.getBookingId())
                .userId(sale.getUserId())
                .originalAmount(sale.getAmount())
                .method(sale.getMethod())
                .status(sale.getStatus())
                .transactionDetails(sale.getTransactionDetails())
                .appliedPromotions(appliedPromotions)
                .totalDiscount(totalDiscount)
                .finalAmount(sale.getAmount() - totalDiscount)
                .build();
    }
    public RevenueReportDTO toRevenueReportDTO(Double totalRevenue, Long totalTransactions,
                                               Double refundedAmount, Long refundCount) {
        totalRevenue = totalRevenue != null ? totalRevenue : 0.0;
        totalTransactions = totalTransactions != null ? totalTransactions : 0L;
        refundedAmount = refundedAmount != null ? refundedAmount : 0.0;
        refundCount = refundCount != null ? refundCount : 0L;

        Double averageSale = totalTransactions != 0
                ? totalRevenue / totalTransactions
                : 0.0;

        return RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTransactions)
                .averageSale(averageSale)
                .refundedAmount(refundedAmount)
                .refundCount(refundCount)
                .build();
    }

    public TierRevenueDTO toTierRevenueDTO(Object[] row) {
        String tier    = (String) row[0];
        double revenue = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
        long   count   = row[2] != null ? ((Number) row[2]).longValue()   : 0L;
        long   tickets = row[3] != null ? ((Number) row[3]).longValue()   : 0L;
        double avg     = count > 0 ? revenue / count : 0.0;

        return TierRevenueDTO.builder()
                .tier(tier)
                .totalRevenue(revenue)
                .saleCount(count)
                .ticketsSold(tickets)
                .averageRevenuePerSale(avg)
                .build();
    }
}