package com.team7.eventticketing.ticket.adapter;

import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.TicketStatus;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    /**
     * Adapts a MongoDB Document into a Ticket entity object.
     * Maps fields from the MongoDB ticket_events collection back to the Ticket structure[cite: 1, 2].
     */
    public Ticket adapt(Document source) {
        if (source == null) {
            return null;
        }

        Ticket ticket = new Ticket();

        // Map the Ticket ID (Long) from MongoDB back to the PostgreSQL ID[cite: 1, 2]
        Long id = source.containsKey("ticketId")
                ? ((Number) source.get("ticketId")).longValue()
                : null;
        ticket.setId(id);

        // Map the Booking ID foreign key
        Long bookingId = source.containsKey("bookingId")
                ? ((Number) source.get("bookingId")).longValue()
                : null;
        ticket.setBookingId(bookingId);

        // Map String fields
        ticket.setAttendeeName(source.getString("attendeeName"));
        ticket.setTicketCode(source.getString("ticketCode"));

        // Map Enum Status[cite: 1]
        String statusStr = source.getString("status");
        if (statusStr != null) {
            ticket.setStatus(TicketStatus.valueOf(statusStr));
        }

        // Handle BSON Date to LocalDateTime conversion for issuedAt[cite: 1]
        Object ts = source.get("timestamp"); // MongoDB log uses 'timestamp'
        if (ts instanceof Date date) {
            ticket.setIssuedAt(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else if (ts instanceof LocalDateTime ldt) {
            ticket.setIssuedAt(ldt);
        }

        // Map the JSONB/Map metadata[cite: 1, 2]
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = source.containsKey("details")
                ? (Map<String, Object>) source.get("details")
                : null;
        ticket.setMetadata(metadata);

        return ticket;
    }
}