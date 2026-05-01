package com.team7.eventticketing.sales.strategy;

import com.team7.eventticketing.sales.dto.RefundRequestDTO;
import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PartialWindowRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequestDTO request, LocalDateTime eventDate) {
        double refundAmount = Math.round((sale.getAmount() * 0.5) * 100.0) / 100.0;

        return new RefundResult(
                refundAmount,
                true,
                "PARTIAL_REFUND_ALLOWED",
                "PartialWindowRefundStrategy"
        );
    }
}