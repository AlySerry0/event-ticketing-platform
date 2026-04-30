package com.team7.eventticketing.sales.factory;

import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventFactory {

    public PaymentAuditEvent createPaymentAuditEvent(String action, TicketSale sale) {
        Map<String, Object> details = new HashMap<>();

        if (sale.getTransactionDetails() != null) {
            details.putAll(sale.getTransactionDetails());
        }

        details.put("status", sale.getStatus() != null ? sale.getStatus().name() : null);
        details.put("bookingId", sale.getBookingId());
        details.put("userId", sale.getUserId());

        return new PaymentAuditEvent(
                sale.getId(),
                action,
                LocalDateTime.now(),
                sale.getMethod() != null ? sale.getMethod().name() : null,
                sale.getAmount(),
                details
        );
    }

    public PaymentAuditEvent createPromotionAppliedEvent(
            TicketSale sale,
            String promotionCode,
            Double discountApplied
    ) {
        Map<String, Object> details = new HashMap<>();

        if (sale.getTransactionDetails() != null) {
            details.putAll(sale.getTransactionDetails());
        }

        details.put("promotionCode", promotionCode);
        details.put("discountApplied", discountApplied);
        details.put("status", sale.getStatus() != null ? sale.getStatus().name() : null);
        details.put("bookingId", sale.getBookingId());
        details.put("userId", sale.getUserId());

        return new PaymentAuditEvent(
                sale.getId(),
                "PROMOTION_APPLIED",
                LocalDateTime.now(),
                sale.getMethod() != null ? sale.getMethod().name() : null,
                sale.getAmount(),
                details
        );
    }
    public PaymentAuditEvent createAnalyticsViewedEvent(
            String action,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, Object> details = new HashMap<>();
        details.put("feature", "S5-F10");
        details.put("startDate", startDate.toString());
        details.put("endDate", endDate.toString());

        return new PaymentAuditEvent(
                null,
                action,
                LocalDateTime.now(),
                null,
                null,
                details
        );
    }
}