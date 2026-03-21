package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping
    public Booking create(@RequestBody Booking booking) {
        return bookingService.save(booking);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getById(@PathVariable Long id) {
        return bookingService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Booking> getAll() {
        return bookingService.findAll();
    }

    @GetMapping("/search")
    public List<Booking> searchBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        BookingStatus bookingStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                bookingStatus = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore or handle invalid status
            }
        }

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startDate != null && !startDate.trim().isEmpty()) {
            start = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }

        return bookingService.searchBookings(bookingStatus, start, end);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Booking> update(@PathVariable Long id, @RequestBody Booking bookingDetails) {
        return bookingService.findById(id).map(booking -> {
            booking.setContactEmail(bookingDetails.getContactEmail());
            booking.setStatus(bookingDetails.getStatus());
            booking.setTotalAmount(bookingDetails.getTotalAmount());
            booking.setMetadata(bookingDetails.getMetadata());
            booking.setBookingDate(bookingDetails.getBookingDate());
            booking.setConfirmedAt(bookingDetails.getConfirmedAt());
            booking.setEventId(bookingDetails.getEventId());
            booking.setUserId(bookingDetails.getUserId());
            return ResponseEntity.ok(bookingService.save(booking));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (bookingService.findById(id).isPresent()) {
            bookingService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
