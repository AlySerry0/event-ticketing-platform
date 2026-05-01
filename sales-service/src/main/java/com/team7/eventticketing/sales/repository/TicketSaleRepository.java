package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {
    @Query(value = "SELECT COUNT(*) > 0 FROM bookings WHERE id = :bookingId", nativeQuery = true)
    boolean bookingExists(@Param("bookingId") Long bookingId);

    @Query(value = "SELECT status FROM bookings WHERE id = :bookingId", nativeQuery = true)
    String getBookingStatus(@Param("bookingId") Long bookingId);

    Optional<TicketSale> findByBookingId(Long bookingId);

    @Query("""
        SELECT t FROM TicketSale t
        WHERE (:ignoreStatus = true OR t.status = :status)
        AND (:ignoreStartDate = true OR t.createdAt >= :startDate)
        AND (:ignoreEndDate = true OR t.createdAt <= :endDate)
        ORDER BY t.createdAt DESC
    """)
    List<TicketSale> searchTicketSales(
            @Param("ignoreStatus") boolean ignoreStatus,
            @Param("status") TicketSaleStatus status,
            @Param("ignoreStartDate") boolean ignoreStartDate,
            @Param("startDate") LocalDateTime startDate,
            @Param("ignoreEndDate") boolean ignoreEndDate,
            @Param("endDate") LocalDateTime endDate
    );

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

    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE id = :userId", nativeQuery = true)
    boolean userExists(@Param("userId") Long userId);

    @Query("""
        SELECT ts.method, COUNT(ts), COALESCE(SUM(ts.amount), 0)
        FROM TicketSale ts
        WHERE ts.userId = :userId AND ts.status = :status
        GROUP BY ts.method
    """)
    List<Object[]> getUserSalesSummaryByMethod(@Param("userId") Long userId,
                                               @Param("status") TicketSaleStatus status);

    boolean existsByBookingIdAndStatus(Long bookingId, TicketSaleStatus status);

    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean userExistsByEmail(String email);

    @Query(value = """
        SELECT
            COALESCE(bi.metadata->>'ticketTier', 'UNSPECIFIED') AS tier,
            SUM(bi.unit_price * bi.quantity)                    AS totalRevenue,
            COUNT(DISTINCT ts.id)                               AS saleCount,
            SUM(bi.quantity)                                    AS ticketsSold
        FROM ticket_sales ts
        JOIN bookings      b  ON b.id  = ts.booking_id
        JOIN booking_items bi ON bi.booking_id = b.id
        WHERE ts.created_at BETWEEN :startDateTime AND :endDateTime
          AND ts.status = 'COMPLETED'
        GROUP BY COALESCE(bi.metadata->>'ticketTier', 'UNSPECIFIED')
        """, nativeQuery = true)
    List<Object[]> findTierRevenue(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime")   LocalDateTime endDateTime
    );

    @Query(value = """
    SELECT e.event_date
    FROM ticket_sales ts
    JOIN bookings b ON b.id = ts.booking_id
    LEFT JOIN events e ON e.id = b.event_id
    WHERE ts.id = :saleId
    """, nativeQuery = true)
    LocalDateTime findEventDateBySaleId(@Param("saleId") Long saleId);
}
