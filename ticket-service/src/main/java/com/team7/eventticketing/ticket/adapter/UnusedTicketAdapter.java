package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import org.springframework.stereotype.Component;

@Component
public class UnusedTicketAdapter implements ObjectArrayAdapter<UnusedTicketDTO> {

    @Override
    public UnusedTicketDTO convert(Object[] row) {

        return UnusedTicketDTO.builder()
                .ticketId(((Number) row[0]).longValue())
                .attendeeName((String) row[1])
                .ticketCode((String) row[2])
                .bookingId(((Number) row[3]).longValue())
                .eventName((String) row[4])
                .eventDate((java.time.LocalDateTime) row[5])
                .build();
    }
}