package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team7.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team7.eventticketing.booking.dto.AttendanceRecordDTO;
import com.team7.eventticketing.booking.dto.BookingAnalyticsDTO;
import com.team7.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import com.team7.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.dto.BookingEstimateRequestDTO;
import com.team7.eventticketing.booking.dto.BookingDetailsDTO;
import com.team7.eventticketing.booking.dto.BookingItemDTO;
import com.team7.eventticketing.booking.service.BookingService;
import com.team7.eventticketing.booking.observer.MongoEventLogger;
import com.team7.eventticketing.booking.dto.EventRecommendationDTO;
import com.team7.eventticketing.booking.service.JwtService;

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
  private JwtService jwtService;


	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@PostMapping
	public BookingDTO create(@RequestBody BookingDTO bookingDTO) {
		return bookingService.save(bookingDTO);
	}

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@GetMapping("/{id}")
	public ResponseEntity<BookingDTO> getById(@PathVariable Long id) {
		return bookingService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@GetMapping
	public List<BookingDTO> getAll() {
		return bookingService.findAll();
	}

	/**
	 * [S3-F1] Get Bookings by Status and Date Range
	 */
	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

  @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@PutMapping("/{id}/complete")
	public ResponseEntity<BookingDTO> completeBooking(@PathVariable Long id) {
		return ResponseEntity.ok(bookingService.completeBooking(id));
	}

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@PutMapping("/{id}")
	public ResponseEntity<BookingDTO> update(@PathVariable Long id, @RequestBody BookingDTO bookingDetails) {
		return bookingService.updateBooking(id, bookingDetails)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (bookingService.findById(id).isPresent()) {
			bookingService.deleteById(id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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
	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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
	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@GetMapping("/analytics/dashboard")
	public ResponseEntity<BookingAnalyticsDashboardDTO> getAnalyticsDashboard(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		// 1. Get the data (hits the cache if available)
		BookingAnalyticsDashboardDTO report = bookingService.getAnalyticsDashboard(startDate, endDate);

		// 2. Delegate the logging to the Service layer (runs even on cache hits!)
		bookingService.recordAnalyticsView(startDate, endDate, report.getTotalRevenue());

		return ResponseEntity.ok(report);
	}

	/**
	 * [M1 S3-F5] Metadata Search
	 */
	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@GetMapping("/metadata/search")
	public ResponseEntity<List<BookingDTO>> searchByMetadata(
			@RequestParam String key,
			@RequestParam String value) {
		return ResponseEntity.ok(bookingService.filterBookingsByMetadata(key, value));
	}


  @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@GetMapping("/{id}/items")
	public ResponseEntity<List<BookingItemDTO>> getBookingItems(@PathVariable Long id) {
		return bookingService.findById(id)
				.map(BookingDTO::getBookingItems)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

  @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

  @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@GetMapping("/{bookingId}/details")
	public ResponseEntity<BookingDetailsDTO> getBookingDetails(@PathVariable Long bookingId) {
		try {
			return ResponseEntity.ok(bookingService.getBookingDetails(bookingId));
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
	@PostMapping("/{bookingId}/record-attendance")
	public ResponseEntity<AttendanceRecordDTO> recordAttendance(@PathVariable Long bookingId) {
		try {
			int count = bookingService.recordAttendance(bookingId);
			return ResponseEntity.ok(new AttendanceRecordDTO(count));
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (ResponseStatusException e) {
			throw e;
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error recording attendance: " + e.getMessage());
		}
	}

  @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
  @GetMapping("/recommendations")
  public ResponseEntity<List<EventRecommendationDTO>> getEventRecommendations(
          @RequestParam Long userId,
          @RequestParam(required = false) Integer limit,
          @RequestHeader("Authorization") String authorizationHeader) {

      if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
          throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token");
      }

      String token = authorizationHeader.substring(7);
      Long requesterId = jwtService.extractUserId(token);
      String requesterRole = jwtService.extractRole(token);

      return ResponseEntity.ok(
              bookingService.getEventRecommendations(userId, limit, requesterId, requesterRole)
      );
  }

	@GetMapping("/user/{userId}/summary")
	public ResponseEntity<BookingSummaryDTO> getUserBookingSummary(@PathVariable Long userId) {
		return ResponseEntity.ok(bookingService.getUserBookingSummary(userId));
	}

	@GetMapping("/user/{userId}/active-count")
	public ResponseEntity<Integer> getUserActiveBookingCount(@PathVariable Long userId) {
		return ResponseEntity.ok(bookingService.getActiveBookingCountByUser(userId));
	}

	@GetMapping("/user/{userId}/count")
	public ResponseEntity<Long> getUserBookingCount(@PathVariable Long userId,
													@RequestParam(required = false) String status) {
		return ResponseEntity.ok(bookingService.getTotalBookingCountByUser(userId, status));
	}

	@GetMapping("/user/{userId}/total")
	public ResponseEntity<java.math.BigDecimal> getUserBookingTotal(@PathVariable Long userId,
																	@RequestParam String startDate,
																	@RequestParam String endDate) {
		return ResponseEntity.ok(bookingService.getUserBookingTotal(userId, startDate, endDate));
	}

	@GetMapping("/event/{eventId}/revenue")
	public ResponseEntity<EventBookingRevenueDTO> getEventRevenue(@PathVariable Long eventId,
																  @RequestParam String startDate,
																  @RequestParam String endDate) {
		return ResponseEntity.ok(bookingService.getEventRevenue(eventId, startDate, endDate));
	}

	@GetMapping("/event/{eventId}/active-count")
	public ResponseEntity<Integer> getEventActiveBookingCount(@PathVariable Long eventId) {
		return ResponseEntity.ok(bookingService.getActiveBookingCountByEvent(eventId));
	}
}
