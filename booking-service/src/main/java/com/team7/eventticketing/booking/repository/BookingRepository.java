package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByStatusAndBookingDateBetweenOrderByBookingDateDesc(BookingStatus status, LocalDateTime startDate, LocalDateTime endDate);
    List<Booking> findByStatusOrderByBookingDateDesc(BookingStatus status);
    List<Booking> findByBookingDateBetweenOrderByBookingDateDesc(LocalDateTime startDate, LocalDateTime endDate);
    List<Booking> findAllByOrderByBookingDateDesc();
}
