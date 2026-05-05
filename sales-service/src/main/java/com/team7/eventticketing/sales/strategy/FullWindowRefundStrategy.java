package com.team7.eventticketing.sales.strategy;

import com.team7.eventticketing.sales.dto.RefundRequestDTO;
import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FullWindowRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequestDTO request, LocalDateTime eventDate) {
        return new RefundResult(
                sale.getAmount(),
                true,
                "FULL_REFUND_ALLOWED",
                "FullWindowRefundStrategy"
        );
    }
}