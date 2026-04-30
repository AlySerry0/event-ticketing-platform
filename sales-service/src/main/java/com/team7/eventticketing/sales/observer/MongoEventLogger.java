package com.team7.eventticketing.sales.observer;

import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import com.team7.eventticketing.sales.repository.PaymentAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final PaymentAuditEventRepository paymentAuditEventRepository;

    public MongoEventLogger(PaymentAuditEventRepository paymentAuditEventRepository) {
        this.paymentAuditEventRepository = paymentAuditEventRepository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            if (payload instanceof PaymentAuditEvent event) {
                paymentAuditEventRepository.save(event);
            } else {
                log.warn("Unsupported Mongo audit payload for eventType={}", eventType);
            }
        } catch (Exception e) {
            log.warn("Mongo audit logging failed for eventType={}: {}", eventType, e.getMessage());
        }
    }
}