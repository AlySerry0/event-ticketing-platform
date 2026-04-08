package com.team7.eventticketing.ticket.repository;

import com.team7.eventticketing.ticket.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
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
