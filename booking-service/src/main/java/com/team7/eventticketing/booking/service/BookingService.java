package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.adapter.BookingNodeAdapter;
import com.team7.eventticketing.booking.dto.*;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.AttendanceRepository;
import com.team7.eventticketing.booking.repository.AttendanceResult;
import com.team7.eventticketing.booking.repository.BookingRepository;
import com.team7.eventticketing.booking.repository.EventNodeRepository;
import com.team7.eventticketing.booking.repository.UserNodeRepository;
import com.team7.eventticketing.booking.model.BookingItemStatus;
import com.team7.eventticketing.booking.observer.EntityObserver;
import com.team7.eventticketing.booking.observer.EntitySubject;
import com.team7.eventticketing.booking.observer.MongoEventLogger;
import com.team7.eventticketing.booking.util.CacheInvalidationService;
import com.team7.eventticketing.booking.adapter.EventDetailsAdapter;

import com.team7.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team7.eventticketing.contracts.dto.EventDTO;
import com.team7.eventticketing.contracts.feign.EventServiceClient;
import com.team7.eventticketing.contracts.feign.TicketServiceClient;
import com.team7.eventticketing.contracts.feign.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Comparator;

import com.team7.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team7.eventticketing.contracts.dto.EventBookingRevenueDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class BookingService implements EntitySubject {
	private static final Logger log = LoggerFactory.getLogger(BookingService.class);
	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private BookingItemService bookingItemService;

	@Autowired
	private UserNodeRepository userNodeRepository;

	@Autowired
	private EventNodeRepository eventNodeRepository;

	@Autowired
	private BookingNodeAdapter bookingNodeAdapter;

	@Autowired
	private CacheInvalidationService cacheInvalidationService;

	@Autowired
	private EventServiceClient eventServiceClient;

	@Autowired
	private com.team7.eventticketing.booking.messaging.publisher.BookingEventPublisher bookingEventPublisher;

	@Autowired
	private UserServiceClient userServiceClient;

	@Autowired
	private TicketServiceClient ticketServiceClient;

	private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

	@Autowired
	public void registerMongoLogger(MongoEventLogger mongoEventLogger) {
		register(mongoEventLogger);
	}

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private EventDetailsAdapter eventDetailsAdapter;

	public BookingDTO save(BookingDTO bookingDTO) {
		Booking booking = convertToEntity(bookingDTO);

		if (booking.getBookingDate() == null) {
			booking.setBookingDate(LocalDateTime.now());
		}

		if (booking.getStatus() == null) {
			booking.setStatus(BookingStatus.PENDING);
		}

		Booking savedBooking = bookingRepository.save(booking);

		this.notifyObservers("BOOKING_CREATED",
				Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		invalidateBookingCaches(savedBooking.getId());

		return convertToDTO(savedBooking);
	}

	@Cacheable(value = "booking", key = "#id")
	public Optional<BookingDTO> findById(Long id) {
		return bookingRepository.findById(id).map(this::convertToDTO);
	}

	public List<BookingDTO> findAll() {
		return bookingRepository.findAll().stream()
				.map(this::convertToDTO)
				.toList();
	}

	public void deleteById(Long id) {
		bookingRepository.deleteById(id);

		this.notifyObservers("BOOKING_DELETED", Map.of("bookingId", id));
		invalidateBookingCaches(id);
		cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");

	}

	public Optional<BookingDTO> updateBooking(Long id, BookingDTO bookingDetails) {
		return bookingRepository.findById(id).map(booking -> {
			if (bookingDetails.getContactEmail() != null)
				booking.setContactEmail(bookingDetails.getContactEmail());
			if (bookingDetails.getStatus() != null)
				booking.setStatus(bookingDetails.getStatus());
			if (bookingDetails.getTotalAmount() != null)
				booking.setTotalAmount(bookingDetails.getTotalAmount());
			if (bookingDetails.getBookingDate() != null)
				booking.setBookingDate(bookingDetails.getBookingDate());
			if (bookingDetails.getConfirmedAt() != null)
				booking.setConfirmedAt(bookingDetails.getConfirmedAt());
			if (bookingDetails.getEventId() != null)
				booking.setEventId(bookingDetails.getEventId());
			if (bookingDetails.getUserId() != null)
				booking.setUserId(bookingDetails.getUserId());

			// Handle JSONB metadata merge
			if (bookingDetails.getMetadata() != null) {
				if (booking.getMetadata() == null) {
					booking.setMetadata(bookingDetails.getMetadata());
				} else {
					booking.getMetadata().putAll(bookingDetails.getMetadata());
				}
			}
			Booking savedBooking = bookingRepository.save(booking);

			this.notifyObservers("BOOKING_UPDATED",
					Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
			invalidateBookingCaches(id);
			cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");

			return convertToDTO(savedBooking);
		});
	}

	@Cacheable(value = "S3-F1", key = "#statusStr + '_' + #startDate + '_' + #endDate")
	public List<BookingDTO> searchBookings(String statusStr, LocalDate startDate, LocalDate endDate) {
		BookingStatus status = null;

		if (statusStr != null && !statusStr.trim().isEmpty()) {
			try {
				status = BookingStatus.valueOf(statusStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid booking status: " + statusStr);
			}
		}

		if ((startDate != null && endDate == null) || (startDate == null && endDate != null)) {
			throw new IllegalArgumentException("Both startDate and endDate must be provided together.");
		}
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("startDate cannot be after endDate.");
		}

		LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
		LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

		List<Booking> bookings;
		if (status != null && startDateTime != null && endDateTime != null) {
			bookings = bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(status, startDateTime,
					endDateTime);
		} else if (status != null) {
			bookings = bookingRepository.findByStatusOrderByBookingDateDesc(status);
		} else if (startDateTime != null && endDateTime != null) {
			bookings = bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime);
		} else {
			bookings = bookingRepository.findAllByOrderByBookingDateDesc();
		}

		return bookings.stream().map(this::convertToDTO).toList();
	}

	@Transactional
	public BookingDTO confirmBookingAndAssignEvent(Long bookingId, Long eventId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		if (booking.getStatus() != BookingStatus.PENDING) {
			throw new IllegalArgumentException("Booking is not PENDING");
		}

		String eventStatus;
		try {
			EventDTO eventDetails = eventServiceClient.getEvent(eventId);
			if (eventDetails == null) {
				throw new NoSuchElementException("Event not found");
			}
			eventStatus = eventDetails.status();
			// NOTE: Depending on your EventDTO record structure, this might be eventDetails.getStatus()
		} catch (Exception e) {
			throw new NoSuchElementException("Event not found or unavailable");
		}

		if (!"UPCOMING".equalsIgnoreCase(eventStatus)) {
			throw new IllegalArgumentException("Event is not UPCOMING");
		}
		booking.setEventId(eventId);
		booking.setStatus(BookingStatus.CONFIRMED);
		booking.setConfirmedAt(LocalDateTime.now());

		Booking savedBooking = bookingRepository.save(booking);

		bookingEventPublisher.publishBookingPlaced(
				new com.team7.eventticketing.contracts.events.BookingPlacedEvent(
						savedBooking.getId(),
						savedBooking.getUserId(),
						savedBooking.getEventId(),
						LocalDateTime.now()
				)
		);

		this.notifyObservers("BOOKING_CONFIRMED",
				Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		invalidateBookingCaches(savedBooking.getId());
		cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");

		return convertToDTO(savedBooking);
	}

	@Transactional
	public BookingDTO completeBooking(Long id) {
		Booking booking = bookingRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

		if (booking.getStatus() != BookingStatus.CHECKED_IN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not CHECKED_IN");
		}

		// --- REFACTORED (S3-F4): Three Feign Pre-Checks ---
		try {
			// 1. Check Event Service
			eventServiceClient.getEvent(booking.getEventId());

			// 2. Check User Service
			userServiceClient.getUser(booking.getUserId());

			// 3. Check Ticket Service (Ensure count doesn't throw errors)
			ticketServiceClient.getUsedTicketCount(booking.getId());
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Validation failed across microservices: " + e.getMessage());
		}
		// --------------------------------------------------

		// Calculate Total Amount
		if (booking.getTotalAmount() == null || booking.getTotalAmount() == 0.0) {
			double total = 0.0;
			if (booking.getBookingItems() != null) {
				for (BookingItem item : booking.getBookingItems()) {
					total += (item.getQuantity() * item.getUnitPrice());
				}
			}
			booking.setTotalAmount(total);
		}

		// --- REFACTORED (S3-F4): Saga State & Removed Direct SQL Insert ---
		booking.setStatus(BookingStatus.COMPLETING); // Changed from COMPLETED
		Booking savedBooking = bookingRepository.saveAndFlush(booking);
		log.info("Booking {} transitioning to COMPLETING", savedBooking.getId());

		// Publish event to trigger the Payment Saga in sales-service
		bookingEventPublisher.publishBookingCompleted(
				new com.team7.eventticketing.contracts.events.BookingCompletedEvent(
						savedBooking.getId(),
						savedBooking.getUserId(),
						savedBooking.getEventId(),
						BigDecimal.valueOf(savedBooking.getTotalAmount()),
						LocalDateTime.now()
				)
		);
		// ------------------------------------------------------------------

		this.notifyObservers("BOOKING_COMPLETING",
				Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		invalidateBookingCaches(id);
		cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");

		return convertToDTO(savedBooking);
	}

	@Cacheable(value = "S3-F3", key = "#request.eventId + '-' + #request.ticketTier + '-' + #request.ticketCount")
	public BookingCostEstimateDTO getCostEstimate(BookingEstimateRequestDTO request) {
		if (request.getEventId() == null || request.getTicketCount() == null || request.getTicketCount() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId and ticketCount (>=1) are required");
		}
		Double avgCapacity = null;
		try {
			AvgCapacityDTO capacityDto = eventServiceClient.getEventAvgCapacity(request.getEventId());
			if (capacityDto != null && capacityDto.avgCapacity() != null) {
				avgCapacity = capacityDto.avgCapacity();
			}
		} catch (Exception e) {
			// Feign might throw an exception if the endpoint returns a 404 (e.g., no sessions found)
			// We safely catch it and leave avgCapacity as null so it falls to the fallback logic
		}

		if (avgCapacity == null) {
			try {
				EventDTO eventDetails = eventServiceClient.getEvent(request.getEventId());
				if (eventDetails == null) {
					throw new IllegalArgumentException("Event not found for ID: " + request.getEventId());
				}
			} catch (Exception e) {
				// Catches FeignException.NotFound if the event genuinely doesn't exist
				throw new IllegalArgumentException("Event not found for ID: " + request.getEventId());
			}
			avgCapacity = 100.0;
		}

		double basePrice = avgCapacity / 10.0;

		double tierMultiplier = "VIP".equalsIgnoreCase(request.getTicketTier()) ? 2.5 : 1.0;

		double ticketCost = basePrice * tierMultiplier * request.getTicketCount();

		double serviceFee = ticketCost * 0.15;

		long activeBookings = bookingRepository.countActiveBookingsForEvent(request.getEventId());
		double demandMultiplier = 1.0;

		if (activeBookings > 200) {
			demandMultiplier = 1.5;
		} else if (activeBookings >= 51) {
			demandMultiplier = 1.25;
		}

		double estimatedTotal = (ticketCost + serviceFee) * demandMultiplier;

		BookingCostEstimateDTO estimateDTO = new BookingCostEstimateDTO();
		estimateDTO.setTicketCost(ticketCost);
		estimateDTO.setServiceFee(serviceFee);
		estimateDTO.setDemandMultiplier(demandMultiplier);
		estimateDTO.setEstimatedTotal(estimatedTotal);

		return estimateDTO;
	}

	public BookingDTO convertToDTO(Booking booking) {
		BookingDTO dto = new BookingDTO();
		dto.setId(booking.getId());
		dto.setUserId(booking.getUserId());
		dto.setEventId(booking.getEventId());
		dto.setContactEmail(booking.getContactEmail());
		dto.setStatus(booking.getStatus());
		dto.setTotalAmount(booking.getTotalAmount());
		dto.setMetadata(booking.getMetadata());
		dto.setBookingDate(booking.getBookingDate());
		dto.setConfirmedAt(booking.getConfirmedAt());
		dto.setCreatedAt(booking.getCreatedAt());

		if (booking.getBookingItems() != null) {
			dto.setBookingItems(
					booking.getBookingItems().stream()
							.sorted(Comparator.comparing(BookingItem::getEventOrder))
							.map(bookingItemService::convertToDTO)
							.toList());
		}

		return dto;
	}

	private Booking convertToEntity(BookingDTO dto) {
		Booking booking = new Booking();
		booking.setId(dto.getId());
		booking.setUserId(dto.getUserId());
		booking.setEventId(dto.getEventId());
		booking.setContactEmail(dto.getContactEmail());
		booking.setStatus(dto.getStatus());
		booking.setTotalAmount(dto.getTotalAmount());
		booking.setMetadata(dto.getMetadata());
		booking.setBookingDate(dto.getBookingDate());
		booking.setConfirmedAt(dto.getConfirmedAt());
		booking.setCreatedAt(dto.getCreatedAt());
		return booking;
	}

	@Cacheable(value = "S3-F6", key = "#startDate.toString() + '_' + #endDate.toString()")
	public BookingAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
		}

		LocalDateTime start = startDate.atStartOfDay();
		LocalDateTime end = endDate.atTime(23, 59, 59);
		List<Object[]> results = bookingRepository.getBookingAnalytics(start, end);
		Object[] row = results.get(0);

		Long total = ((Number) row[0]).longValue();
		Long completed = ((Number) row[1]).longValue();
		Long cancelled = ((Number) row[2]).longValue();
		Double revenue = ((Number) row[3]).doubleValue();

		Double average = completed > 0 ? revenue / completed : 0.0;
		Double completionRate = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

		// Utilizing the Phase 5 Builder
		return BookingAnalyticsDTO.builder()
				.totalBookings(total)
				.completedBookings(completed)
				.cancelledBookings(cancelled)
				.totalRevenue(revenue)
				.averageBookingAmount(average)
				.completionRate(completionRate)
				.build();
	}

	@Cacheable(value = "S3-F5", key = "#key + '_' + #value")
	public List<BookingDTO> filterBookingsByMetadata(String key, String value) {
		if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key and value cannot be blank");
		}
		return bookingRepository.findByMetadataKeyAndValue(key, value).stream()
				.map(this::convertToDTO)
				.toList();
	}

	@Cacheable(value = "S3-F9", key = "#bookingId")
	public BookingDetailsDTO getBookingDetails(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		List<BookingItem> bookingItems = booking.getBookingItems() == null ? List.of() : booking.getBookingItems();

		List<BookingItemDTO> itemDTOs = bookingItems.stream()
				.sorted(Comparator.comparing(BookingItem::getEventOrder))
				.map(bookingItemService::convertToDTO)
				.toList();

		int confirmedItems = (int) bookingItems.stream()
				.filter(item -> item.getStatus() == BookingItemStatus.CONFIRMED)
				.count();

		return BookingDetailsDTO.builder()
				.bookingId(booking.getId())
				.userId(booking.getUserId())
				.eventId(booking.getEventId())
				.status(booking.getStatus())
				.totalAmount(booking.getTotalAmount())
				.metadata(booking.getMetadata())
				.items(itemDTOs)
				.totalItems(itemDTOs.size())
				.confirmedItems(confirmedItems)
				.build();
	}

	@Cacheable(value = "S3-F12", key = "#userId + '-' + (#limit == null ? 5 : #limit)")
	public List<EventRecommendationDTO> getEventRecommendations(Long userId, Integer limit, Long requesterId,
			String requesterRole) {
		if (!userId.equals(requesterId) && !"ADMIN".equals(requesterRole)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own recommendations");
		}

		try {
			userServiceClient.getUser(userId);
		} catch (feign.FeignException.NotFound e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		} catch (feign.FeignException e) {
			log.warn("user-service unavailable for recommendations userId={}: {}", userId, e.getMessage());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable");
		}

		int recommendationLimit = limit == null ? 5 : limit;

		List<EventRecommendationDTO> recommendations = attendanceRepository.getRecommendations(userId,
				recommendationLimit);

		List<Long> eventIds = recommendations.stream()
				.map(EventRecommendationDTO::getEventId)
				.toList();

		if (eventIds.isEmpty()) {
			return recommendations;
		}

		Map<Long, Object[]> eventDetails = new HashMap<>();
		for (Long eid : eventIds) {
			try {
				com.team7.eventticketing.contracts.dto.EventDTO event = eventServiceClient.getEvent(eid);
				if (event != null) {
					// Pack it into the Object[] array that the legacy adapter expects
					eventDetails.put(eid, new Object[]{
							event.id(),
							event.name(),
							event.category(),
							event.eventDate()
					});
				}
			} catch (Exception e) {
				// If event-service fails for one event, just skip enriching it
			}
		}

		return recommendations.stream()
				.map(recommendation -> {
					Object[] row = eventDetails.get(recommendation.getEventId());

					if (row == null) {
						return recommendation;
					}

					return eventDetailsAdapter.adapt(row, recommendation.getScore());
				})
				.toList();
	}

	private void invalidateBookingCaches(Long bookingId) {
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + bookingId);
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F9::*");
	}

	@Transactional
	public BookingDTO addItemsToBooking(Long bookingId, List<BookingItemDTO> itemDTOs) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		if (booking.getStatus() != BookingStatus.PENDING &&
				booking.getStatus() != BookingStatus.CONFIRMED &&
				booking.getStatus() != BookingStatus.CHECKED_IN) {
			throw new IllegalArgumentException("Items can only be added to PENDING, CONFIRMED, or CHECKED_IN bookings");
		}

		if (itemDTOs == null || itemDTOs.isEmpty()) {
			throw new IllegalArgumentException("At least one item must be provided");
		}
		// --- REFACTORED (S3-F8) ---
		// Check event-service to ensure the event is valid before adding items
		if (booking.getEventId() != null) {
			try {
				EventDTO eventDetails = eventServiceClient.getEvent(booking.getEventId());
				if (eventDetails == null) {
					throw new IllegalArgumentException("Event not found for ID: " + booking.getEventId());
				}
			} catch (Exception e) {
				// Feign throws an exception on 404 Not Found
				throw new IllegalArgumentException("Event not found or unavailable for ID: " + booking.getEventId());
			}
		}
		// ---------------------------

		int currentMaxOrder = booking.getBookingItems() == null ? 0
				: booking.getBookingItems().stream()
						.mapToInt(BookingItem::getEventOrder)
						.max()
						.orElse(0);

		for (BookingItemDTO itemDTO : itemDTOs) {
			if (itemDTO.getSessionId() == null ||
					itemDTO.getSessionTitle() == null || itemDTO.getSessionTitle().trim().isEmpty() ||
					itemDTO.getQuantity() == null ||
					itemDTO.getUnitPrice() == null) {
				throw new IllegalArgumentException(
						"Each item must have sessionId, sessionTitle, quantity, and unitPrice");
			}

			BookingItem item = new BookingItem();
			item.setEventOrder(++currentMaxOrder);
			item.setSessionId(itemDTO.getSessionId());
			item.setSessionTitle(itemDTO.getSessionTitle());
			item.setQuantity(itemDTO.getQuantity());
			item.setUnitPrice(itemDTO.getUnitPrice());
			item.setStatus(BookingItemStatus.RESERVED);
			item.setMetadata(itemDTO.getMetadata());

			booking.addBookingItem(item);
		}

		Booking savedBooking = bookingRepository.save(booking);

		Map<String, Object> payload = new HashMap<>();
		payload.put("bookingId", savedBooking.getId());
		payload.put("userId", savedBooking.getUserId());
		payload.put("eventId", savedBooking.getEventId());
		payload.put("itemsAdded", itemDTOs.size());
		payload.put("status", savedBooking.getStatus().name());

		notifyObservers("ITEMS_ADDED", payload);

		invalidateBookingCaches(savedBooking.getId());

		return convertToDTO(savedBooking);

	}

	@Transactional
	public void cancelBooking(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		if (booking.getStatus() != BookingStatus.PENDING &&
				booking.getStatus() != BookingStatus.CONFIRMED) {
			throw new IllegalArgumentException("Only PENDING or CONFIRMED bookings can be cancelled");
		}

		booking.setStatus(BookingStatus.CANCELLED);

		// --- REFACTORED (S3-F7): Removed direct tickets UPDATE ---
		Booking savedBooking = bookingRepository.save(booking);

		// Publish the event so ticket-service can cancel the tickets itself
		bookingEventPublisher.publishBookingCancelled(
				new com.team7.eventticketing.contracts.events.BookingCancelledEvent(
						savedBooking.getId(),
						savedBooking.getUserId(),
						savedBooking.getEventId(),
						"user_cancelled",
						LocalDateTime.now()
				)
		);
		// ---------------------------------------------------------

		Map<String, Object> payload = new HashMap<>();
		payload.put("bookingId", savedBooking.getId());
		payload.put("userId", savedBooking.getUserId());
		payload.put("eventId", savedBooking.getEventId());
		payload.put("status", savedBooking.getStatus().name());

		notifyObservers("BOOKING_CANCELLED", payload);

		invalidateBookingCaches(savedBooking.getId());
		cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");
	}

	@Cacheable(value = "S3-F10", key = "#startDate.toString() + '_' + #endDate.toString()")
	public BookingAnalyticsDashboardDTO getAnalyticsDashboard(LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
		}

		LocalDateTime start = startDate.atStartOfDay();
		LocalDateTime end = endDate.atTime(23, 59, 59);

		List<Booking> bookings = bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(start, end);

		long totalBookings = bookings.size();
		double totalRevenue = 0.0;
		long completedCount = 0;
		long conversionCount = 0;
		Map<String, Long> statusMap = new HashMap<>();

		for (Booking b : bookings) {
			String status = b.getStatus().name();
			statusMap.put(status, statusMap.getOrDefault(status, 0L) + 1);

			if (b.getStatus() == BookingStatus.COMPLETED) {
				completedCount++;
			}

			if (b.getStatus() == BookingStatus.CONFIRMED
					|| b.getStatus() == BookingStatus.CHECKED_IN
					|| b.getStatus() == BookingStatus.COMPLETED) {
				conversionCount++;
			}
		}

		Double revenueFromSales = bookingRepository.sumTicketSalesRevenueForCompletedBookings(start, end);
		totalRevenue = revenueFromSales != null ? revenueFromSales : 0.0;

		double avgValue = completedCount > 0 ? totalRevenue / completedCount : 0.0;
		double convRate = totalBookings > 0 ? (double) conversionCount / totalBookings : 0.0;

		return BookingAnalyticsDashboardDTO.builder()
				.totalBookings(totalBookings)
				.totalRevenue(totalRevenue)
				.averageBookingValue(avgValue)
				.conversionRate(convRate)
				.bookingsByStatus(statusMap)
				.build();
	}

	public void recordAnalyticsView(LocalDate startDate, LocalDate endDate, Double totalRevenueCalculated) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("dashboardType", "BookingAnalytics");
		payload.put("startDate", startDate.toString());
		payload.put("endDate", endDate.toString());
		payload.put("totalRevenueCalculated", totalRevenueCalculated);

		notifyObservers("ANALYTICS_VIEWED", payload);
	}

	@Override
	public void register(EntityObserver o) {
		observers.add(o);
	}

	@Override
	public void unregister(EntityObserver o) {
		observers.remove(o);
	}

	@Override
	public void notifyObservers(String action, Object payload) {
		for (EntityObserver observer : observers) {
			observer.onEvent(action, payload);
		}
	}

	@Transactional
	public int recordAttendance(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		if (booking.getStatus() != BookingStatus.COMPLETED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not completed");
		}

		if (booking.getEventId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has no assigned event");
		}

		String userName = "Unknown User";
		try {
			com.team7.eventticketing.contracts.dto.UserDTO user = userServiceClient.getUser(booking.getUserId());
			if (user != null) {
				userName = user.name(); // Assuming your UserDTO record uses name()
			}
		} catch (Exception e) {
			log.warn("Could not fetch user details for attendance: {}", e.getMessage());
		}

		String eventName = "Unknown Event";
		String eventCategory = "UNSPECIFIED";
		try {
			com.team7.eventticketing.contracts.dto.EventDTO event = eventServiceClient.getEvent(booking.getEventId());
			if (event != null) {
				eventName = event.name() != null ? event.name() : "Unknown Event";
				eventCategory = event.category() != null ? event.category() : "UNSPECIFIED";
			}
		} catch (Exception e) {
			log.warn("Could not fetch event details for attendance: {}", e.getMessage());
		}
		Map<String, Object> params = new HashMap<>();
		params.put("userId", booking.getUserId());
		params.put("userName", userName);
		params.put("eventId", booking.getEventId());
		params.put("eventName", eventName);
		params.put("category", eventCategory);
		params.put("bookingId", bookingId);
		params.put("now", LocalDateTime.now());

		AttendanceResult result = attendanceRepository.recordAttendance(params);

		if (!result.alreadyRecorded()) {
			Map<String, Object> observerPayload = new HashMap<>();
			observerPayload.put("bookingId", bookingId);
			observerPayload.put("userId", booking.getUserId());
			observerPayload.put("eventId", booking.getEventId());
			observerPayload.put("action", "INTERACTION_RECORDED");
			this.notifyObservers("INTERACTION_RECORDED", observerPayload);
			cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F12::*");
		}

		return result.attendanceCount();
	}

	public BookingSummaryDTO getUserBookingSummary(Long userId) {
		Object[] row = bookingRepository.getUserBookingSummaryRaw(userId).get(0);
		long total = ((Number) row[0]).longValue();
		long completed = ((Number) row[1]).longValue();
		long cancelled = ((Number) row[2]).longValue();
		BigDecimal totalSpent = row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString());
		BigDecimal avg = total == 0 ? BigDecimal.ZERO :
				totalSpent.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
		return new BookingSummaryDTO(total, completed, cancelled, totalSpent, avg);
	}

	public int getActiveBookingCountByUser(Long userId) {
		return bookingRepository.countActiveBookingsByUserId(userId);
	}

	public long getTotalBookingCountByUser(Long userId, String status) {
		if (status == null || status.isBlank()) {
			return bookingRepository.countByUserId(userId);
		}
		return bookingRepository.countByUserIdAndStatus(userId, status.toUpperCase());
	}

	public BigDecimal getUserBookingTotal(Long userId, String startDate, String endDate) {
		LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
		LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
		BigDecimal total = bookingRepository.sumCompletedByUserIdAndDateRange(userId, start, end);
		return total == null ? BigDecimal.ZERO : total;
	}

	public EventBookingRevenueDTO getEventRevenue(Long eventId, String startDate, String endDate) {
		LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
		LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
		Object[] row = bookingRepository.getEventRevenueRaw(eventId, start, end).get(0);
		long total = ((Number) row[0]).longValue();
		BigDecimal revenue = row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
		BigDecimal avg = total == 0 ? BigDecimal.ZERO :
				revenue.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
		return new EventBookingRevenueDTO(total, revenue, avg);
	}

	public int getActiveBookingCountByEvent(Long eventId) {
		return bookingRepository.countActiveBookingsByEventId(eventId);
	}
}
