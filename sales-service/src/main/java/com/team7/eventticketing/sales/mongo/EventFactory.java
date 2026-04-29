package com.team7.eventticketing.sales.mongo;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class EventFactory {

    private EventFactory() {
    }

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (type != EventType.PAYMENT_AUDIT) {
            throw new IllegalArgumentException("Unsupported event type: " + type);
        }

        PaymentAuditEvent event = new PaymentAuditEvent();
        event.setTimestamp(LocalDateTime.now());
        event.setAction((String) params.get("action"));

        Object saleId = params.get("saleId");
        if (saleId instanceof Number n) {
            event.setSaleId(n.longValue());
        }

        Object method = params.get("method");
        if (method != null) {
            event.setMethod(method.toString());
        }

        Object amount = params.get("amount");
        if (amount instanceof Number n) {
            event.setAmount(n.doubleValue());
        }

        Object detailsObj = params.get("details");
        if (detailsObj instanceof Map<?, ?> rawMap) {
            Map<String, Object> details = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                details.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            event.setDetails(details);
        }

        return event;
    }
}