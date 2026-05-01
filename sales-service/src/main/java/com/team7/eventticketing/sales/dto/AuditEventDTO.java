package com.team7.eventticketing.sales.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class AuditEventDTO {

    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details;

    public AuditEventDTO() {}

    public AuditEventDTO(String action, LocalDateTime timestamp, String method,
                         Double amount, Map<String, Object> details) {
        this.action = action;
        this.timestamp = timestamp;
        this.method = method;
        this.amount = amount;
        this.details = details;
    }

    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getMethod() { return method; }
    public Double getAmount() { return amount; }
    public Map<String, Object> getDetails() { return details; }

    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setMethod(String method) { this.method = method; }
    public void setAmount(Double amount) { this.amount = amount; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}