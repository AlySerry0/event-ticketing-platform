package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	@Autowired
	private BookingService bookingService;

	@PostMapping
	public BookingDTO create(@RequestBody BookingDTO bookingDTO) {
		return bookingService.save(bookingDTO);
	}

	@GetMapping("/{id}")
	public ResponseEntity<BookingDTO> getById(@PathVariable Long id) {
		return bookingService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping
	public List<BookingDTO> getAll() {
		return bookingService.findAll();
	}

	/**
	 * [S3-F1] Get Bookings by Status and Date Range
	 * GET /api/bookings/search?status={s}&startDate={d}&endDate={d}
	 */
	@GetMapping("/search")
	public ResponseEntity<List<BookingDTO>> searchBookings(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		try {
			List<BookingDTO> bookings = bookingService.searchBookings(status, startDate, endDate);
			return ResponseEntity.ok(bookings);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@PutMapping("/{id}/confirm")
	public ResponseEntity<BookingDTO> confirmBooking(@PathVariable Long id, @RequestParam Long eventId) {
		try {
			BookingDTO confirmedBooking = bookingService.confirmBookingAndAssignEvent(id, eventId);
			return ResponseEntity.ok(confirmedBooking);
		} catch (NoSuchElementException e) {
			// Catches "Booking not found" and "Event not found"
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (IllegalArgumentException e) {
			// Catches "Booking is not PENDING" and "Event is not UPCOMING"
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<BookingDTO> update(@PathVariable Long id, @RequestBody BookingDTO bookingDetails) {
		return bookingService.updateBooking(id, bookingDetails)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
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
