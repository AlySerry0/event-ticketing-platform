package com.team7.eventticketing.ticket.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.TicketStatus;

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

    @Query(value = "SELECT event_id FROM bookings WHERE id = :bookingId", nativeQuery = true)
    Long findEventIdByBookingId(@Param("bookingId") Long bookingId);

   

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
    
    @Query(value = "SELECT * FROM tickets WHERE metadata ->> :key = :value", nativeQuery = true)
    List<Ticket> findByMetadataEquals(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM tickets WHERE CAST(metadata ->> :key AS NUMERIC) > CAST(:value AS NUMERIC)", nativeQuery = true)
    List<Ticket> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM tickets WHERE CAST(metadata ->> :key AS NUMERIC) < CAST(:value AS NUMERIC)", nativeQuery = true)
    List<Ticket> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);

    List<Ticket> findByTicketCodeIn(List<String> ticketCodes);

    List<Ticket> findByIssuedAtBetweenOrderByIssuedAtAsc(LocalDateTime start, LocalDateTime end);
    List<Ticket> findByStatusAndIssuedAtBetweenOrderByIssuedAtAsc(TicketStatus status, LocalDateTime start, LocalDateTime end);

    boolean existsByTicketCode(String ticketCode);

    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean userExistsByEmail(String email);

    @Query(value = """
        SELECT
            COUNT(*) AS totalIssued,
            COALESCE(SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END), 0) AS usedCount,
            COALESCE(SUM(CASE WHEN status = 'VALID' THEN 1 ELSE 0 END), 0) AS validCount,
            COALESCE(SUM(CASE WHEN status = 'EXPIRED' THEN 1 ELSE 0 END), 0) AS expiredCount,
            COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledCount
        FROM tickets
        WHERE issued_at BETWEEN :start AND :end
        """, nativeQuery = true)
    Object[] getTicketAnalytics(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

