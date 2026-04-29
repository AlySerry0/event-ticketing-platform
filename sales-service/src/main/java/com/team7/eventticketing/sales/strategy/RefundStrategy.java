package com.team7.eventticketing.sales.strategy;

import com.team7.eventticketing.sales.dto.RefundRequestDTO;
import com.team7.eventticketing.sales.model.TicketSale;

import java.time.LocalDateTime;

public interface RefundStrategy {
    RefundResult calculateRefund(TicketSale sale, RefundRequestDTO request, LocalDateTime eventDate);
}