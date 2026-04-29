package com.team7.eventticketing.sales.observer;

import com.team7.eventticketing.sales.mongo.EventFactory;
import com.team7.eventticketing.sales.mongo.EventType;
import com.team7.eventticketing.sales.mongo.MongoEvent;
import com.team7.eventticketing.sales.mongo.PaymentAuditEvent;
import com.team7.eventticketing.sales.repository.PaymentAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final PaymentAuditEventRepository paymentAuditEventRepository;
    private final EventType boundType = EventType.PAYMENT_AUDIT;

    public MongoEventLogger(PaymentAuditEventRepository paymentAuditEventRepository) {
        this.paymentAuditEventRepository = paymentAuditEventRepository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("action", eventType);

            if (payload instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    params.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            MongoEvent event = EventFactory.createEvent(boundType, params);

            if (event instanceof PaymentAuditEvent paymentAuditEvent) {
                paymentAuditEventRepository.save(paymentAuditEvent);
            }
        } catch (Exception e) {
            log.warn("Failed to write payment audit event to MongoDB", e);
        }
    }
}