package com.team7.eventticketing.sales.adapter;

import com.team7.eventticketing.sales.dto.PromotionUsageDTO;
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

        return new PromotionUsageDTO.Builder()
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
}