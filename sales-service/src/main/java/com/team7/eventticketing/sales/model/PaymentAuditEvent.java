package com.team7.eventticketing.sales.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "payment_audit_trail")
public class PaymentAuditEvent {

    @Id
    private String id;

    private Long saleId;
    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details;

    public PaymentAuditEvent() {}

    public PaymentAuditEvent(Long saleId, String action, LocalDateTime timestamp,
                             String method, Double amount, Map<String, Object> details) {
        this.saleId = saleId;
        this.action = action;
        this.timestamp = timestamp;
        this.method = method;
        this.amount = amount;
        this.details = details;
    }

    public String getId() { return id; }
    public Long getSaleId() { return saleId; }
    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getMethod() { return method; }
    public Double getAmount() { return amount; }
    public Map<String, Object> getDetails() { return details; }

    public void setId(String id) { this.id = id; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setMethod(String method) { this.method = method; }
    public void setAmount(Double amount) { this.amount = amount; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}