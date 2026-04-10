package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
	List<Booking> findByStatusAndBookingDateBetweenOrderByBookingDateDesc(BookingStatus status, LocalDateTime startDate,
	                                                                      LocalDateTime endDate);

	List<Booking> findByStatusOrderByBookingDateDesc(BookingStatus status);

	List<Booking> findByBookingDateBetweenOrderByBookingDateDesc(LocalDateTime startDate, LocalDateTime endDate);

	List<Booking> findAllByOrderByBookingDateDesc();

	@Query(value = "SELECT status FROM events WHERE id = :eventId", nativeQuery = true)
	String findEventStatusById(@Param("eventId") Long eventId);

	@Query(value = "SELECT AVG(capacity) FROM event_sessions WHERE event_id = :eventId", nativeQuery = true)
	Double getAverageSessionCapacityByEventId(@Param("eventId") Long eventId);

	@Query(value = "SELECT COUNT(*) FROM bookings WHERE event_id = :eventId AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')", nativeQuery = true)
	long countActiveBookingsForEvent(@Param("eventId") Long eventId);

	@Modifying
	@Query(value = "INSERT INTO ticket_sales (booking_id, user_id, amount, status, created_at) " + "VALUES (:bookingId, :userId, :amount, 'PENDING', CURRENT_TIMESTAMP)", nativeQuery = true)
	void createPendingTicketSale(@Param("bookingId") Long bookingId, @Param("userId") Long userId, @Param("amount") Double amount);
  
	@Query(value = "SELECT " + "COUNT(id), " + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0), " + "COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0), " + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0.0) " + "FROM bookings " + "WHERE booking_date >= :startDate AND booking_date <= :endDate", nativeQuery = true)
	List<Object[]> getBookingAnalytics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
  
	@Query(value = "SELECT * FROM bookings WHERE metadata ->> :key = :value", nativeQuery = true)
	List<Booking> findByMetadataKeyAndValue(@Param("key") String key, @Param("value") String value);


    @Modifying
    @Transactional
    @Query(value = "UPDATE booking_items SET status = 'REFUNDED' WHERE booking_id = :bookingId AND status IN ('RESERVED', 'CONFIRMED')", nativeQuery = true)
    int cancelValidTicketsByBookingId(@Param("bookingId") Long bookingId);
}
