package com.team7.eventticketing.ticket.controller;

import com.team7.eventticketing.ticket.dto.NearbyTicketDTO;
import com.team7.eventticketing.ticket.dto.TicketDTO;
import com.team7.eventticketing.ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PostMapping
    public TicketDTO create(@RequestBody TicketDTO ticketDTO) {
        return ticketService.save(ticketDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDTO> getById(@PathVariable Long id) {
        return ticketService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<TicketDTO> getAll() {
        return ticketService.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketDTO> update(@PathVariable Long id, @RequestBody TicketDTO ticketDetails) {
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

    @DeleteMapping("/purge")
    public ResponseEntity<Integer> purgeOldTickets(@RequestParam int olderThanDays) {
        int deletedCount = ticketService.purgeOldTickets(olderThanDays);
        return ResponseEntity.ok(deletedCount);
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyTicketDTO>> getNearbyTickets(@RequestParam double lat, @RequestParam double lon,
            @RequestParam double radiusKm) {
        try {
            List<NearbyTicketDTO> tickets = ticketService.getNearbyTickets(lat, lon, radiusKm);
            return ResponseEntity.ok(tickets);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
