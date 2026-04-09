package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {
    @Query(value = "SELECT COUNT(*) > 0 FROM bookings WHERE id = :bookingId", nativeQuery = true)
    boolean bookingExists(@Param("bookingId") Long bookingId);

    @Query(value = "SELECT status FROM bookings WHERE id = :bookingId", nativeQuery = true)
    String getBookingStatus(@Param("bookingId") Long bookingId);

    Optional<TicketSale> findByBookingId(Long bookingId);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM TicketSale t
        WHERE t.status = com.team7.eventticketing.sales.model.TicketSaleStatus.COMPLETED
        AND t.createdAt BETWEEN :start AND :end
    """)
    Double getTotalRevenue(@Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    @Query("""
        SELECT COUNT(t)
        FROM TicketSale t
        WHERE t.status = com.team7.eventticketing.sales.model.TicketSaleStatus.COMPLETED
        AND t.createdAt BETWEEN :start AND :end
    """)
    Long getTotalTransactions(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM TicketSale t
        WHERE t.status = com.team7.eventticketing.sales.model.TicketSaleStatus.REFUNDED
        AND t.createdAt BETWEEN :start AND :end
    """)
    Double getRefundedAmount(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);


    @Query("""
        SELECT COUNT(t)
        FROM TicketSale t
        WHERE t.status = com.team7.eventticketing.sales.model.TicketSaleStatus.REFUNDED
        AND t.createdAt BETWEEN :start AND :end
    """)
    Long getRefundCount(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

}
