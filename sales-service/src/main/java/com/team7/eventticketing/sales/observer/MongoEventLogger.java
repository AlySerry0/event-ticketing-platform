package com.team7.eventticketing.sales.observer;

import com.team7.eventticketing.sales.factory.EventFactory;
import com.team7.eventticketing.sales.factory.EventType;
import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import com.team7.eventticketing.sales.repository.PaymentAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final PaymentAuditEventRepository repository;
    private final EventFactory eventFactory;

    public MongoEventLogger(PaymentAuditEventRepository repository, EventFactory eventFactory) {
        this.repository = repository;
        this.eventFactory = eventFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(String eventType, Object payload) {
        try {
            if (!(payload instanceof Map<?, ?> rawPayload)) {
                log.warn("Unsupported Mongo audit payload for eventType={}", eventType);
                return;
            }

            Map<String, Object> params = new HashMap<>((Map<String, Object>) rawPayload);
            params.put("action", eventType);

            Object event = eventFactory.createEvent(EventType.PAYMENT_AUDIT, params);

            if (event instanceof PaymentAuditEvent paymentAuditEvent) {
                repository.save(paymentAuditEvent);
            }

        } catch (Exception e) {
            log.warn("Mongo audit logging failed for eventType={}: {}", eventType, e.getMessage());
        }
    }
}