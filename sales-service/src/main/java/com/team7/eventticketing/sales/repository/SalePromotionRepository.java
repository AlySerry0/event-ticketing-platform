package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.SalePromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalePromotionRepository extends JpaRepository<SalePromotion, Long> {
    boolean existsByTicketSaleIdAndPromotionId(Long ticketSaleId, Long promotionId);
}
