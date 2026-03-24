package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

	/**
	 * [S3-F1] Get Bookings by Status and Date Range
	 * GET /api/bookings/search?status={s}&startDate={d}&endDate={d}
	 */
	@GetMapping("/search")
	public ResponseEntity<List<Booking>> searchBookings(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		// 1. Validate and parse status
		BookingStatus bookingStatus = null;
		if (status != null && !status.trim().isEmpty()) {
			try {
				bookingStatus = BookingStatus.valueOf(status.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking status: " + status);
			}
		}

		// 2. Validate date range logic (both or neither)
		if ((startDate != null && endDate == null) || (startDate == null && endDate != null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both startDate and endDate must be provided together.");
		}

		// 3. Ensure startDate is not after endDate
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate.");
		}

		// 4. Convert LocalDate to LocalDateTime for accurate querying
		LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
		LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

		// 5. Fetch results
		List<Booking> bookings = bookingService.searchBookings(bookingStatus, startDateTime, endDateTime);

		return ResponseEntity.ok(bookings);
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
