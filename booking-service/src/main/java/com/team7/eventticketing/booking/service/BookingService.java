package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.dto.*;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team7.eventticketing.booking.model.BookingItemStatus;
import org.springframework.cache.annotation.Cacheable;

import java.util.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.team7.eventticketing.booking.observer.EntityObserver;
import com.team7.eventticketing.booking.observer.EntitySubject;
import com.team7.eventticketing.booking.observer.MongoEventLogger;
import com.team7.eventticketing.booking.util.CacheInvalidationService;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class BookingService implements EntitySubject {

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private BookingItemService bookingItemService;

	private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
	private final CacheInvalidationService cacheInvalidationService;

	@Autowired
	public BookingService(MongoEventLogger mongoEventLogger, CacheInvalidationService cacheInvalidationService) {
		this.cacheInvalidationService = cacheInvalidationService;
		this.register(mongoEventLogger);
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

	public BookingDTO save(BookingDTO bookingDTO) {
		Booking booking = convertToEntity(bookingDTO);

		if (booking.getBookingDate() == null) {
			booking.setBookingDate(LocalDateTime.now());
		}

		if (booking.getStatus() == null) {
			booking.setStatus(BookingStatus.PENDING);
		}

		Booking savedBooking = bookingRepository.save(booking);

		this.notifyObservers("BOOKING_CREATED", Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::*");

		return convertToDTO(savedBooking);
	}

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
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + id);
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
			
			this.notifyObservers("BOOKING_UPDATED", Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
			cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
			cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + id);

			return convertToDTO(savedBooking);
		});
	}

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

		String eventStatus = bookingRepository.findEventStatusById(eventId);
		if (eventStatus == null) {
			throw new NoSuchElementException("Event not found");
		}
		if (!"UPCOMING".equals(eventStatus)) {
			throw new IllegalArgumentException("Event is not UPCOMING");
		}

		booking.setEventId(eventId);
		booking.setStatus(BookingStatus.CONFIRMED);
		booking.setConfirmedAt(LocalDateTime.now());

		Booking savedBooking = bookingRepository.save(booking);

		this.notifyObservers("BOOKING_CONFIRMED", Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::*");

		return convertToDTO(savedBooking);
	}

	@Transactional
	public BookingDTO completeBooking(Long id) {
		Booking booking = bookingRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

		if (booking.getStatus() != BookingStatus.CHECKED_IN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not CHECKED_IN");
		}
		booking.setStatus(BookingStatus.COMPLETED);
		if (booking.getTotalAmount() == null || booking.getTotalAmount() == 0.0) {
			double total = 0.0;
			if (booking.getBookingItems() != null) {
				for (BookingItem item : booking.getBookingItems()) {
					total += (item.getQuantity() * item.getUnitPrice());
				}
			}
			booking.setTotalAmount(total);
		}
		Booking savedBooking = bookingRepository.saveAndFlush(booking);
		bookingRepository.createPendingTicketSale(
				savedBooking.getId(),
				savedBooking.getUserId(),
				savedBooking.getTotalAmount());
		
		this.notifyObservers("BOOKING_COMPLETED", Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + id);

		return convertToDTO(savedBooking);
	}

	public BookingCostEstimateDTO getCostEstimate(BookingEstimateRequestDTO request) {
		Double avgCapacity = bookingRepository.getAverageSessionCapacityByEventId(request.getEventId());

		if (avgCapacity == null) {
			throw new IllegalArgumentException("Event or sessions not found for ID: " + request.getEventId());
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
							.toList()
			);
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

	@Cacheable(value = "booking-service", key = "'S3-F6::' + #startDate.toString() + '_' + #endDate.toString()")
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
	@Cacheable(value = "booking-service", key = "'S3-F5::' + #key + '_' + #value")
	public List<BookingDTO> filterBookingsByMetadata(String key, String value) {
		if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key and value cannot be blank");
		}
		return bookingRepository.findByMetadataKeyAndValue(key, value).stream()
				.map(this::convertToDTO)
				.toList();
	}


	public BookingDetailsDTO getBookingDetails(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		BookingDetailsDTO dto = new BookingDetailsDTO();
		dto.setBookingId(booking.getId());
		dto.setUserId(booking.getUserId());
		dto.setEventId(booking.getEventId());
		dto.setStatus(booking.getStatus());
		dto.setTotalAmount(booking.getTotalAmount());
		dto.setMetadata(booking.getMetadata());

		List<BookingItem> bookingItems = booking.getBookingItems() == null ? List.of() : booking.getBookingItems();

		List<BookingItemDTO> itemDTOs = bookingItems.stream()
				.sorted(Comparator.comparing(BookingItem::getEventOrder))
				.map(bookingItemService::convertToDTO)
				.toList();

		int confirmedItems = (int) bookingItems.stream()
				.filter(item -> item.getStatus() == BookingItemStatus.CONFIRMED)
				.count();

		dto.setItems(itemDTOs);
		dto.setTotalItems(itemDTOs.size());
		dto.setConfirmedItems(confirmedItems);

		return dto;
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

		int currentMaxOrder = booking.getBookingItems() == null ? 0 :
				booking.getBookingItems().stream()
						.mapToInt(BookingItem::getEventOrder)
						.max()
						.orElse(0);

		for (BookingItemDTO itemDTO : itemDTOs) {
			if (itemDTO.getSessionId() == null ||
					itemDTO.getSessionTitle() == null || itemDTO.getSessionTitle().trim().isEmpty() ||
					itemDTO.getQuantity() == null ||
					itemDTO.getUnitPrice() == null) {
				throw new IllegalArgumentException("Each item must have sessionId, sessionTitle, quantity, and unitPrice");
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
		
		this.notifyObservers("ITEMS_ADDED", Map.of("bookingId", savedBooking.getId(), "status", savedBooking.getStatus()));
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + bookingId);

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

		if (bookingRepository.ticketsTableExists()) {
			bookingRepository.cancelValidTicketsByBookingId(bookingId);
		}

		bookingRepository.save(booking);
		
		this.notifyObservers("BOOKING_CANCELLED", Map.of("bookingId", bookingId, "status", booking.getStatus()));
		cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
		cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + bookingId);
	}

	@Cacheable(value = "booking-service", key = "'S3-F10::' + #startDate.toString() + '_' + #endDate.toString()")
	public BookingAnalyticsDashboardDTO getAnalyticsDashboard(LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
		}

		LocalDateTime start = startDate.atStartOfDay();
		LocalDateTime end = endDate.atTime(LocalTime.MAX);

		List<Booking> bookings = bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(start, end);

		long totalBookings = bookings.size();
		double totalRevenue = 0.0;
		long completedCount = 0;
		long conversionCount = 0;
		Map<String, Long> statusMap = new HashMap<>();

		for(Booking b : bookings) {
			String status = b.getStatus().name();
			statusMap.put(status, statusMap.getOrDefault(status, 0L) + 1);

			if (b.getStatus() == BookingStatus.COMPLETED) {
				completedCount++;
				if(b.getTotalAmount() != null) {
					totalRevenue += b.getTotalAmount();
				}
			}
			if (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.CHECKED_IN || b.getStatus() == BookingStatus.COMPLETED) {
				conversionCount++;
			}
		}

		double avgValue = completedCount > 0 ? totalRevenue / completedCount : 0.0;
		double convRate = totalBookings > 0 ? (double) conversionCount / totalBookings : 0.0;

		// Utilizing the Phase 5 Builder
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
}

