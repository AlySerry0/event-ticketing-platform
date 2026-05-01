package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.TicketScanDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.cassandra.TicketScanEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Adapter Pattern (Milestone 2 Enforcement)
 * Converts domain models and DTOs into a Cassandra TicketScanEvent entity.
 */
@Component
public class TicketScanEventAdapter {

    public TicketScanEvent adaptToEvent(Long ticketId, Ticket ticket, TicketScanDTO scanDTO) {
        TicketScanEvent scanEvent = new TicketScanEvent();
        scanEvent.setTicketId(ticketId);
        scanEvent.setTimestamp(LocalDateTime.now());
        scanEvent.setScanType(scanDTO.getScanType());
        scanEvent.setAttendeeName(ticket.getAttendeeName());
        scanEvent.setGate(scanDTO.getGate());
        scanEvent.setSection(scanDTO.getSection());
        scanEvent.setSeatNumber(scanDTO.getSeatNumber());
        scanEvent.setNotes(scanDTO.getNotes());
        return scanEvent;
    }
}
