package com.team7.eventticketing.ticket.repository;

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
}
