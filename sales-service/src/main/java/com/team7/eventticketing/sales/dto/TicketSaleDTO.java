package com.team7.eventticketing.sales.dto;

import com.team7.eventticketing.sales.model.PaymentMethod;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public class TicketSaleDTO {
    private Long id;
    private Long bookingId;
    private Long userId;
    private Double amount;
    private PaymentMethod method;
    private TicketSaleStatus status;
    private Map<String, Object> transactionDetails;
    private LocalDateTime createdAt;
    private List<SalePromotionDTO> salePromotions;

    public TicketSaleDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public TicketSaleStatus getStatus() { return status; }
    public void setStatus(TicketSaleStatus status) { this.status = status; }

    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public void setTransactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<SalePromotionDTO> getSalePromotions() {
        return salePromotions;
    }
    public void setSalePromotions(List<SalePromotionDTO> salePromotions) {
        this.salePromotions = salePromotions;
    }


}
