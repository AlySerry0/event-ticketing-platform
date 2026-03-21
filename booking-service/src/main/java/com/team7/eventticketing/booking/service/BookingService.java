package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }

    public Optional<Booking> findById(Long id) {
        return bookingRepository.findById(id);
    }

    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    public void deleteById(Long id) {
        bookingRepository.deleteById(id);
    }

    public List<Booking> searchBookings(BookingStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        if (status != null && startDate != null && endDate != null) {
            return bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(status, startDate, endDate);
        } else if (status != null) {
            return bookingRepository.findByStatusOrderByBookingDateDesc(status);
        } else if (startDate != null && endDate != null) {
            return bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDate, endDate);
        } else {
            return bookingRepository.findAllByOrderByBookingDateDesc();
        }
    }
}
