package com.team7.eventticketing.ticket.repository;

import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query(value = """
        SELECT 
            t.id AS ticketId,
            t.attendee_name AS attendeeName,
            t.ticket_code AS ticketCode,
            b.id AS bookingId,
            e.name AS eventName,
            e.event_date AS eventDate
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        JOIN events e ON b.event_id = e.id
        WHERE t.status = 'VALID'
        AND e.status = 'UPCOMING'
        """, nativeQuery = true)
    List<UnusedTicketDTO> findUnusedTicketsForUpcomingEvents();
}
