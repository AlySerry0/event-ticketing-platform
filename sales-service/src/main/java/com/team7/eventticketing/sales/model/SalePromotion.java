package com.team7.eventticketing.sales.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sale_promotions")
public class SalePromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discount_applied", nullable = false)
    private Double discountApplied;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_sale_id", nullable = false)
    @JsonIgnore
    private TicketSale ticketSale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    @JsonIgnore
    private Promotion promotion;

    public SalePromotion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getDiscountApplied() { return discountApplied; }
    public void setDiscountApplied(Double discountApplied) { this.discountApplied = discountApplied; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public TicketSale getTicketSale() { return ticketSale; }
    public void setTicketSale(TicketSale ticketSale) { this.ticketSale = ticketSale; }

    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }
}
