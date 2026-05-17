package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
	List<Booking> findByStatusAndBookingDateBetweenOrderByBookingDateDesc(BookingStatus status, LocalDateTime startDate,
	                                                                      LocalDateTime endDate);

	List<Booking> findByStatusOrderByBookingDateDesc(BookingStatus status);

	List<Booking> findByBookingDateBetweenOrderByBookingDateDesc(LocalDateTime startDate, LocalDateTime endDate);

	List<Booking> findAllByOrderByBookingDateDesc();

@Query(value = "SELECT COUNT(*) FROM bookings WHERE event_id = :eventId AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')", nativeQuery = true)
	long countActiveBookingsForEvent(@Param("eventId") Long eventId);

	@Modifying
	@Query(value = "INSERT INTO ticket_sales (booking_id, user_id, amount, status, created_at) " + "VALUES (:bookingId, :userId, :amount, 'PENDING', CURRENT_TIMESTAMP)", nativeQuery = true)
	void createPendingTicketSale(@Param("bookingId") Long bookingId, @Param("userId") Long userId, @Param("amount") Double amount);
  
	@Query(value = "SELECT " + "COUNT(id), " + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0), " + "COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0), " + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0.0) " + "FROM bookings " + "WHERE booking_date >= :startDate AND booking_date <= :endDate", nativeQuery = true)
	List<Object[]> getBookingAnalytics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
  
	@Query(value = "SELECT * FROM bookings WHERE metadata ->> :key = :value", nativeQuery = true)
	List<Booking> findByMetadataKeyAndValue(@Param("key") String key, @Param("value") String value);

    @Query(value = """
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'tickets'
    )
    """, nativeQuery = true)
    boolean ticketsTableExists();


    @Modifying
    @Transactional
    @Query(value = "UPDATE tickets SET status = 'CANCELLED' WHERE booking_id = :bookingId AND status = 'VALID'", nativeQuery = true)
    int cancelValidTicketsByBookingId(@Param("bookingId") Long bookingId);

    @Query(value = """
        SELECT COALESCE(SUM(ts.amount), 0)
        FROM ticket_sales ts
        JOIN bookings b ON ts.booking_id = b.id
        WHERE b.status = 'COMPLETED'
          AND b.booking_date >= :startDate
          AND b.booking_date <= :endDate
        """, nativeQuery = true)
    Double sumTicketSalesRevenueForCompletedBookings(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // S3-READ-DB: user-scoped aggregate queries
    @Query(value = """
        SELECT COUNT(*), \
               COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0), \
               COALESCE(SUM(CASE WHEN status IN ('CANCELLED','REFUNDED') THEN 1 ELSE 0 END), 0), \
               COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0) \
        FROM bookings WHERE user_id = :userId
        """, nativeQuery = true)
    List<Object[]> getUserBookingSummaryRaw(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId AND status IN ('PENDING','CONFIRMED','CHECKED_IN','COMPLETING','PAYMENT_PENDING')", nativeQuery = true)
    int countActiveBookingsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId", nativeQuery = true)
    long countByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId AND status = CAST(:status AS bookingstatus)", nativeQuery = true)
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query(value = """
        SELECT COALESCE(SUM(total_amount), 0) FROM bookings \
        WHERE user_id = :userId AND status = 'COMPLETED' \
          AND booking_date >= :startDate AND booking_date <= :endDate
        """, nativeQuery = true)
    BigDecimal sumCompletedByUserIdAndDateRange(@Param("userId") Long userId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    // S3-READ-DB: event-scoped aggregate queries
    @Query(value = """
        SELECT COUNT(*), \
               COALESCE(SUM(total_amount), 0) \
        FROM bookings \
        WHERE event_id = :eventId AND status = 'COMPLETED' \
          AND booking_date >= :startDate AND booking_date <= :endDate
        """, nativeQuery = true)
    List<Object[]> getEventRevenueRaw(@Param("eventId") Long eventId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE event_id = :eventId AND status IN ('PENDING','CONFIRMED','CHECKED_IN','COMPLETING','PAYMENT_PENDING')", nativeQuery = true)
    int countActiveBookingsByEventId(@Param("eventId") Long eventId);
}
