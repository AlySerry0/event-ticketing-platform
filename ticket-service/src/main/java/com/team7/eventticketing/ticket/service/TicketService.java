package com.team7.eventticketing.ticket.service;

import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    public Ticket save(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    public Optional<Ticket> findById(Long id) {
        return ticketRepository.findById(id);
    }

    public List<Ticket> findAll() {
        return ticketRepository.findAll();
    }

    public void deleteById(Long id) {
        ticketRepository.deleteById(id);
    }
}
