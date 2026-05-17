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

import java.math.BigDecimal;

@Repository
public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {

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


    @Query("""
        SELECT ts.method, COUNT(ts), COALESCE(SUM(ts.amount), 0)
        FROM TicketSale ts
        WHERE ts.userId = :userId AND ts.status = :status
        GROUP BY ts.method
    """)
    List<Object[]> getUserSalesSummaryByMethod(@Param("userId") Long userId,
                                               @Param("status") TicketSaleStatus status);

    boolean existsByBookingIdAndStatus(Long bookingId, TicketSaleStatus status);

    @Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM TicketSale t
    WHERE t.userId = :userId
      AND t.status = com.team7.eventticketing.sales.model.TicketSaleStatus.COMPLETED
      AND t.createdAt >= :startDate
      AND t.createdAt <= :endDate
""")
    BigDecimal getUserTotalCompletedSales(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
    @Query("""
    SELECT t FROM TicketSale t
    WHERE t.status = com.team7.eventticketing.sales.model.TicketSaleStatus.COMPLETED
    AND t.createdAt BETWEEN :startDateTime AND :endDateTime
""")
    List<TicketSale> findCompletedSalesBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );


}
