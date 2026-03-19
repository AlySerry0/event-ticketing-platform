package com.team7.eventticketing.ticket.controller;

import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PostMapping
    public Ticket create(@RequestBody Ticket ticket) {
        return ticketService.save(ticket);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getById(@PathVariable Long id) {
        return ticketService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Ticket> getAll() {
        return ticketService.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> update(@PathVariable Long id, @RequestBody Ticket ticketDetails) {
        return ticketService.findById(id).map(ticket -> {
            ticket.setBookingId(ticketDetails.getBookingId());
            ticket.setAttendeeName(ticketDetails.getAttendeeName());
            ticket.setTicketCode(ticketDetails.getTicketCode());
            ticket.setStatus(ticketDetails.getStatus());
            ticket.setIssuedAt(ticketDetails.getIssuedAt());
            ticket.setMetadata(ticketDetails.getMetadata());
            return ResponseEntity.ok(ticketService.save(ticket));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (ticketService.findById(id).isPresent()) {
            ticketService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
