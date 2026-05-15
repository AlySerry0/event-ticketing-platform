package com.team7.eventticketing.ticket.controller;
import com.team7.eventticketing.ticket.dto.*;

import com.team7.eventticketing.ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import com.team7.eventticketing.ticket.dto.TicketScanDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping
    public TicketDTO create(@RequestBody TicketDTO ticketDTO) {
        return ticketService.save(ticketDTO);
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<TicketDTO> getById(@PathVariable Long id) {
        return ticketService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping
    public List<TicketDTO> getAll() {
        return ticketService.findAll();
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TicketDTO> update(@PathVariable Long id, @RequestBody TicketDTO ticketDetails) {
        return ticketService.updateTicket(id, ticketDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (ticketService.findById(id).isPresent()) {
            ticketService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/unused-upcoming")
    public List<UnusedTicketDTO> getUnusedUpcomingTickets() {
        return ticketService.getUnusedTicketsForUpcomingEvents();
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Integer>> purgeOldTickets(@RequestParam int olderThanDays) {
        int deletedCount = ticketService.purgeOldTickets(olderThanDays);
        return ResponseEntity.ok(Map.of("deletedCount", deletedCount));
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/booking/{bookingId}/used-count")
    public ResponseEntity<Integer> getUsedTicketCount(@PathVariable Long bookingId) {
        return ResponseEntity.ok(ticketService.getUsedTicketCount(bookingId));
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping("/batch")
    public ResponseEntity<?> issueBatchTickets(@RequestBody BatchTicketRequestDTO batchRequest) {
        try {
            int count = ticketService.issueBatchTickets(batchRequest);
            return ResponseEntity.status(201).body(Map.of("count", count));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/history")
    public ResponseEntity<List<TicketDTO>> getTicketsInDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ticketService.getTicketsInDateRange(startDate, endDate, status));
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/analytics")
    public TicketAnalyticsDTO getAnalytics(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return ticketService.getAnalytics(startDate, endDate);
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/{id}/scans")
    public ResponseEntity<List<TicketScanDTO>> getTicketScanHistory(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return ResponseEntity.ok(ticketService.getTicketScanHistory(id, startTime, endTime));
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping("/{id}/scan")
    public ResponseEntity<Void> recordTicketScan(@PathVariable Long id, @RequestBody TicketScanDTO scanDTO) {
        ticketService.recordTicketScan(id, scanDTO);
        return ResponseEntity.status(201).build();
    }
}


