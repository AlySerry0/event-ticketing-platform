package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.TicketScanDTO;
import com.team7.eventticketing.ticket.model.cassandra.TicketScanEvent;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public TicketScanDTO adapt(TicketScanEvent source) {
        return TicketScanDTO.builder()
                .timestamp(source.getTimestamp())
                .scanType(source.getScanType())
                .attendeeName(source.getAttendeeName())
                .gate(source.getGate())
                .section(source.getSection())
                .seatNumber(source.getSeatNumber())
                .notes(source.getNotes())
                .build();
    }
}