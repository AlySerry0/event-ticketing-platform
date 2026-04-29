package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.dto.BookingAnalyticsDTO;
import com.team7.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import com.team7.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.dto.BookingEstimateRequestDTO;
import com.team7.eventticketing.booking.dto.BookingDetailsDTO;
import com.team7.eventticketing.booking.dto.BookingItemDTO;
import com.team7.eventticketing.booking.service.BookingService;
import com.team7.eventticketing.booking.observer.MongoEventLogger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	@Autowired
	private BookingService bookingService;

	@Autowired
	private MongoEventLogger mongoEventLogger;

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@PostMapping
	public BookingDTO create(@RequestBody BookingDTO bookingDTO) {
		return bookingService.save(bookingDTO);
	}

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@GetMapping("/{id}")
	public ResponseEntity<BookingDTO> getById(@PathVariable Long id) {
		return bookingService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@GetMapping
	public List<BookingDTO> getAll() {
		return bookingService.findAll();
	}

	/**
	 * [S3-F1] Get Bookings by Status and Date Range
	 */
	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
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

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@PutMapping("/{id}/confirm")
	public ResponseEntity<BookingDTO> confirmBooking(@PathVariable Long id, @RequestParam Long eventId) {
		try {
			BookingDTO confirmedBooking = bookingService.confirmBookingAndAssignEvent(id, eventId);
			return ResponseEntity.ok(confirmedBooking);
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (IllegalArgumentException e) {
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

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@PutMapping("/{id}/complete")
	public ResponseEntity<BookingDTO> completeBooking(@PathVariable Long id) {
		return ResponseEntity.ok(bookingService.completeBooking(id));
	}

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@PutMapping("/{id}")
	public ResponseEntity<BookingDTO> update(@PathVariable Long id, @RequestBody BookingDTO bookingDetails) {
		return bookingService.updateBooking(id, bookingDetails)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (bookingService.findById(id).isPresent()) {
			bookingService.deleteById(id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@PostMapping("/estimate")
	public ResponseEntity<?> estimateCost(@RequestBody BookingEstimateRequestDTO request) {
		try {
			BookingCostEstimateDTO estimateDTO = bookingService.getCostEstimate(request);
			return ResponseEntity.ok(estimateDTO);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		}
	}

	/**
	 * [M1 S3-F6] Original Analytics Endpoint
	 * GET /api/bookings/analytics?startDate={d}&endDate={d}
	 */
	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@GetMapping("/analytics")
	public ResponseEntity<BookingAnalyticsDTO> getAnalytics(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		BookingAnalyticsDTO report = bookingService.getAnalytics(startDate, endDate);
		return ResponseEntity.ok(report);
	}

	/**
	 * [M2 S3-F10] Analytics Dashboard Endpoint (NEW)
	 * GET /api/bookings/analytics/dashboard?startDate={d}&endDate={d}
	 */
	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
	@GetMapping("/analytics/dashboard")
	public ResponseEntity<BookingAnalyticsDashboardDTO> getAnalyticsDashboard(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		// Call the service (Caching will be handled INSIDE the service method in Phase 5)
		BookingAnalyticsDashboardDTO report = bookingService.getAnalyticsDashboard(startDate, endDate);

		// Because @Cacheable is removed from the Controller, this method always runs.
		// This guarantees the Observability log fires even on cache hits.
		mongoEventLogger.onEvent("ANALYTICS_VIEWED", Map.of(
				"dashboardType", "BookingAnalytics",
				"startDate", startDate.toString(),
				"endDate", endDate.toString(),
				"totalRevenueCalculated", report.getTotalRevenue()
		));

		return ResponseEntity.ok(report);
	}

	/**
	 * [M1 S3-F5] Metadata Search
	 */
	@PreAuthorize("hasAnyAuthority('ATTENDEE', 'ADMIN')")
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