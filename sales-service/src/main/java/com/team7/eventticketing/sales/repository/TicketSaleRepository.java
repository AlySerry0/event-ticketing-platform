package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {
    @Query(value = "SELECT COUNT(*) > 0 FROM bookings WHERE id = :bookingId", nativeQuery = true)
    boolean bookingExists(@Param("bookingId") Long bookingId);

    @Query(value = "SELECT status FROM bookings WHERE id = :bookingId", nativeQuery = true)
    String getBookingStatus(@Param("bookingId") Long bookingId);

    Optional<TicketSale> findByBookingId(Long bookingId);

    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE id = :userId", nativeQuery = true)
    boolean userExists(@Param("userId") Long userId);

    @Query("""
       SELECT ts.method, COUNT(ts), COALESCE(SUM(ts.amount), 0)
       FROM TicketSale ts
       WHERE ts.userId = :userId AND ts.status = :status
       GROUP BY ts.method
       """)
    List<Object[]> getUserSalesSummaryByMethod(@Param("userId") Long userId,
                                               @Param("status") com.team7.eventticketing.sales.model.TicketSaleStatus status);
}
