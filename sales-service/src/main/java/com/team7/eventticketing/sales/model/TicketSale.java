package com.team7.eventticketing.sales.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ticket_sales")
public class TicketSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TicketSaleStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_details", columnDefinition = "jsonb")
    private Map<String, Object> transactionDetails;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "ticketSale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalePromotion> salePromotions = new ArrayList<>();

    public TicketSale() {}

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

    public List<SalePromotion> getSalePromotions() { return salePromotions; }
    public void setSalePromotions(List<SalePromotion> salePromotions) { this.salePromotions = salePromotions; }

    public void addSalePromotion(SalePromotion sp) {
        salePromotions.add(sp);
        sp.setTicketSale(this);
    }

    public void removeSalePromotion(SalePromotion sp) {
        salePromotions.remove(sp);
        sp.setTicketSale(null);
    }
}
