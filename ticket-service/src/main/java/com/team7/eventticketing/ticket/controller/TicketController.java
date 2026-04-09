package com.team7.eventticketing.ticket.controller;
import com.team7.eventticketing.ticket.dto.BatchTicketRequestDTO;

import com.team7.eventticketing.ticket.dto.NearbyTicketDTO;
import com.team7.eventticketing.ticket.dto.IssueTicketDTO;
import com.team7.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import com.team7.eventticketing.ticket.dto.TicketDTO;
import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team7.eventticketing.ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/unused-upcoming")
    public List<UnusedTicketDTO> getUnusedUpcomingTickets() {
        return ticketService.getUnusedTicketsForUpcomingEvents();
    }

    @GetMapping("/event/{eventId}/summary")
    public ResponseEntity<Object> getEventSummary(@PathVariable Long eventId) {
        try {
            EventAttendanceSummaryDTO dto = ticketService.getEventSummary(eventId);
            return ResponseEntity.ok(dto);

        } catch (RuntimeException ex) {
            if ("No tickets found".equals(ex.getMessage())) {
                return ResponseEntity.status(404).body(
                        Map.of(
                                "status", 404,
                                "error", "Not Found",
                                "message", "No tickets found for eventId " + eventId
                        )
                );
            }
            throw ex;
        }
    }
  
    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Integer>> purgeOldTickets(@RequestParam int olderThanDays) {
        int deletedCount = ticketService.purgeOldTickets(olderThanDays);
        return ResponseEntity.ok(Map.of("deletedCount", deletedCount));
    }

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<?> issueTicket(@PathVariable Long bookingId, @RequestBody IssueTicketDTO request) {
        try {
            return ResponseEntity.status(201).body(ticketService.issueTicket(bookingId, request));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/booking/{bookingId}/latest")
    public ResponseEntity<?> getLatestTicket(@PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(ticketService.getLatestTicketForBooking(bookingId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
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
    
    @GetMapping("/metadata/search")
    public ResponseEntity<?> filterTicketsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value) {
        try {
            return ResponseEntity.ok(ticketService.filterTicketsByMetadata(key, operator, value));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<?> issueBatchTickets(@RequestBody BatchTicketRequestDTO batchRequest) {
        try {
            int count = ticketService.issueBatchTickets(batchRequest);
            return ResponseEntity.status(201).body(Map.of("issuedCount", count));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<TicketDTO>> getTicketsInDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ticketService.getTicketsInDateRange(startDate, endDate, status));
    }
}


