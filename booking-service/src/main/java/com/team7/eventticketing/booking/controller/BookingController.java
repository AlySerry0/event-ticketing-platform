package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.dto.BookingAnalyticsDTO;
import com.team7.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.dto.BookingEstimateRequestDTO;

import com.team7.eventticketing.booking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import com.team7.eventticketing.booking.dto.BookingDetailsDTO;
import com.team7.eventticketing.booking.dto.BookingItemDTO;

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
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
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

	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
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

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        try {
            bookingService.cancelBooking(id);
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
	
	@PutMapping("/{id}/complete")
	public ResponseEntity<BookingDTO> completeBooking(@PathVariable Long id) {
		return ResponseEntity.ok(bookingService.completeBooking(id));
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

	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@PostMapping("/estimate")
	public ResponseEntity<?> estimateCost(@RequestBody BookingEstimateRequestDTO request) {
		try {
			BookingCostEstimateDTO estimateDTO = bookingService.getCostEstimate(request);
			return ResponseEntity.ok(estimateDTO);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	@GetMapping("/analytics")
	public ResponseEntity<BookingAnalyticsDTO> getAnalytics(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		BookingAnalyticsDTO report = bookingService.getAnalytics(startDate, endDate);
		return ResponseEntity.ok(report);
	}

	@GetMapping("/metadata/search")
	public ResponseEntity<List<BookingDTO>> searchByMetadata(
			@RequestParam String key,
			@RequestParam String value) {
		return ResponseEntity.ok(bookingService.filterBookingsByMetadata(key, value));
	}
	@GetMapping("/{id}/items")
	public ResponseEntity<List<BookingItemDTO>> getBookingItems(@PathVariable Long id) {
		return bookingService.findById(id)
				.map(BookingDTO::getBookingItems)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
    @PostMapping("/{bookingId}/items")
    public ResponseEntity<BookingDTO> addItemsToBooking(
            @PathVariable Long bookingId,
            @RequestBody List<BookingItemDTO> items) {
        try {
            BookingDTO updatedBooking = bookingService.addItemsToBooking(bookingId, items);
            return ResponseEntity.ok(updatedBooking);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{bookingId}/details")
    public ResponseEntity<BookingDetailsDTO> getBookingDetails(@PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(bookingService.getBookingDetails(bookingId));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
