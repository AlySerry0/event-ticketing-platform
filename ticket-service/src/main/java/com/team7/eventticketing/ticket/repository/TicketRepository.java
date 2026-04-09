package com.team7.eventticketing.ticket.repository;

import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
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

    
    @Query(value = """
        SELECT 
            COUNT(*) AS totalTickets,
            SUM(CASE WHEN t.status = 'USED' THEN 1 ELSE 0 END) AS usedTickets,
            SUM(CASE WHEN t.status = 'VALID' THEN 1 ELSE 0 END) AS validTickets,
            MAX(t.issued_at) FILTER (WHERE t.status = 'USED') AS lastCheckIn
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.event_id = :eventId
    """, nativeQuery = true)
    List<Object[]> getEventAttendanceSummary(@Param("eventId") Long eventId);
         
    @Modifying
    @Query(value = """
        DELETE FROM tickets 
        WHERE issued_at < :cutoff
        AND status IN ('EXPIRED', 'CANCELLED')
        """, nativeQuery = true)
    int deleteOldExpiredOrCancelled(@Param("cutoff") LocalDateTime cutoff);

    Optional<Ticket> findFirstByBookingIdOrderByIssuedAtDesc(Long bookingId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM bookings WHERE id = :bookingId)", nativeQuery = true)
    boolean existsBookingById(@Param("bookingId") Long bookingId);

    @Query(value = """
        SELECT 
            t.id, 
            t.attendee_name, 
            t.booking_id, 
            e.name, 
            CAST(e.details->>'venueLat' AS DOUBLE PRECISION), 
            CAST(e.details->>'venueLon' AS DOUBLE PRECISION),
            (SQRT(POWER(CAST(e.details->>'venueLat' AS DOUBLE PRECISION) - :lat, 2) + POWER(CAST(e.details->>'venueLon' AS DOUBLE PRECISION) - :lon, 2)) * 111) as distance
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        JOIN events e ON b.event_id = e.id
        WHERE t.status = 'VALID'
        AND (SQRT(POWER(CAST(e.details->>'venueLat' AS DOUBLE PRECISION) - :lat, 2) + POWER(CAST(e.details->>'venueLon' AS DOUBLE PRECISION) - :lon, 2)) * 111) <= :radiusKm
        ORDER BY distance ASC
        """, nativeQuery = true)
    List<Object[]> findNearbyTicketsNative(@Param("lat") double lat, @Param("lon") double lon, @Param("radiusKm") double radiusKm);
    
    List<Ticket> findByTicketCodeIn(List<String> ticketCodes);
}
