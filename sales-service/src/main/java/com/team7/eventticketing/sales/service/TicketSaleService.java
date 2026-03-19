package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TicketSaleService {

    @Autowired
    private TicketSaleRepository ticketSaleRepository;

    public TicketSale save(TicketSale ticketSale) {
        return ticketSaleRepository.save(ticketSale);
    }

    public Optional<TicketSale> findById(Long id) {
        return ticketSaleRepository.findById(id);
    }

    public List<TicketSale> findAll() {
        return ticketSaleRepository.findAll();
    }

    public void deleteById(Long id) {
        ticketSaleRepository.deleteById(id);
    }
}
