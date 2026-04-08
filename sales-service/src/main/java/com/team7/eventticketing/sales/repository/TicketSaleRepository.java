package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<TicketSale> findAllByOrderByCreatedAtDesc();

    List<TicketSale> findByStatusOrderByCreatedAtDesc(TicketSaleStatus status);

    List<TicketSale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate, LocalDateTime endDate);

    List<TicketSale> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime startDate);

    List<TicketSale> findByCreatedAtLessThanEqualOrderByCreatedAtDesc(LocalDateTime endDate);

    List<TicketSale> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            TicketSaleStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<TicketSale> findByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            TicketSaleStatus status,
            LocalDateTime startDate
    );

    List<TicketSale> findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            TicketSaleStatus status,
            LocalDateTime endDate
    );
}
