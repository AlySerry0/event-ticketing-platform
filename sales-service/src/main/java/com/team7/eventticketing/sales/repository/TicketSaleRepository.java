package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {
}
