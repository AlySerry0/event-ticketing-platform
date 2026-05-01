package com.team7.eventticketing.sales.adapter;

import com.team7.eventticketing.sales.dto.AuditEventDTO;
import com.team7.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MongoDocumentAdapter {

    public SaleAuditTrailDTO adapt(Long saleId, List<PaymentAuditEvent> events) {
        List<AuditEventDTO> auditEvents = events.stream()
                .map(event -> new AuditEventDTO(
                        event.getAction(),
                        event.getTimestamp() == null ? null : event.getTimestamp().toString(),
                        event.getMethod(),
                        event.getAmount(),
                        event.getDetails()
                ))
                .toList();

        return new SaleAuditTrailDTO(saleId, auditEvents);
    }
}
