package com.team7.eventticketing.sales.factory;

import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.observer.MongoEvent;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (type != EventType.PAYMENT_AUDIT) {
            throw new IllegalArgumentException("Unsupported event type: " + type);
        }

        return new PaymentAuditEvent(
                (Long) params.get("saleId"),
                (String) params.get("action"),
                LocalDateTime.now(),
                (String) params.get("method"),
                params.get("amount") instanceof Number n ? n.doubleValue() : null,
                (Map<String, Object>) params.get("details")
        );
    }
}