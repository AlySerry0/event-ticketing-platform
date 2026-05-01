package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.dto.NearbyTicketDTO;
import org.springframework.stereotype.Component;

@Component
public class NearbyTicketAdapter implements ObjectArrayAdapter<NearbyTicketDTO> {

    @Override
    public NearbyTicketDTO convert(Object[] row) {
        return NearbyTicketDTO.builder()
                .ticketId(((Number) row[0]).longValue())
                .attendeeName((String) row[1])
                .bookingId(((Number) row[2]).longValue())
                .eventName((String) row[3])
                .eventLat(((Number) row[4]).doubleValue())
                .eventLon(((Number) row[5]).doubleValue())
                .distanceKm(((Number) row[6]).doubleValue())
                .build();
    }
}