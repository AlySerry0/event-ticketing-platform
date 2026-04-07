package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.model.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    @Query("SELECT COALESCE(MAX(bi.eventOrder), 0) FROM BookingItem bi WHERE bi.booking.id = :bookingId")
    int findMaxEventOrderByBookingId(@Param("bookingId") Long bookingId);
}