package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.dto.BookingAnalyticsDTO;
import com.team7.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.dto.BookingEstimateRequestDTO;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team7.eventticketing.booking.dto.BookingDetailsDTO;
import com.team7.eventticketing.booking.dto.BookingItemDTO;
import com.team7.eventticketing.booking.model.BookingItemStatus;
import java.util.Comparator;
import com.team7.eventticketing.booking.observer.EntityObserver;
import com.team7.eventticketing.booking.observer.EntitySubject;
import com.team7.eventticketing.booking.observer.MongoEventLogger;
import com.team7.eventticketing.booking.util.CacheInvalidationService;
import org.springframework.cache.annotation.Cacheable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import com.team7.eventticketing.booking.adapter.Neo4jRecordAdapter;
import com.team7.eventticketing.booking.dto.ProviderRecommendationDTO;
import org.neo4j.driver.Driver;

@Service
public class BookingService implements EntitySubject {

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private BookingItemService bookingItemService;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    private final List<EntityObserver> observers = new ArrayList<>();

    @Autowired
    public void registerMongoLogger(MongoEventLogger mongoEventLogger) {
        register(mongoEventLogger);
    }

    @Autowired
    private Driver neo4jDriver;

    @Autowired
    private Neo4jRecordAdapter neo4jRecordAdapter;

	public BookingDTO save(BookingDTO bookingDTO) {
		Booking booking = convertToEntity(bookingDTO);

		if (booking.getBookingDate() == null) {
			booking.setBookingDate(LocalDateTime.now());
		}

		if (booking.getStatus() == null) {
			booking.setStatus(BookingStatus.PENDING);
		}

		return convertToDTO(bookingRepository.save(booking));
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
			return convertToDTO(bookingRepository.save(booking));
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

		return convertToDTO(bookingRepository.save(booking));
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

	public BookingAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
		}

		LocalDateTime start = startDate.atStartOfDay(); // 2026-03-01 00:00:00
		LocalDateTime end = endDate.atTime(23, 59, 59); // 2026-03-31 23:59:59
		List<Object[]> results = bookingRepository.getBookingAnalytics(start, end);
		Object[] row = results.get(0);

		Long total = ((Number) row[0]).longValue();
		Long completed = ((Number) row[1]).longValue();
		Long cancelled = ((Number) row[2]).longValue();
		Double revenue = ((Number) row[3]).doubleValue();

		Double average = 0.0;
		if (completed > 0) {
			average = revenue / completed;
		}

		Double completionRate = 0.0;
		if (total > 0) {
			completionRate = ((double) completed / total) * 100.0;
		}

		BookingAnalyticsDTO dto = new BookingAnalyticsDTO();
		dto.setTotalBookings(total);
		dto.setCompletedBookings(completed);
		dto.setCancelledBookings(cancelled);
		dto.setTotalRevenue(revenue);
		dto.setAverageBookingAmount(average);
		dto.setCompletionRate(completionRate);

		return dto;
	}

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
    public List<ProviderRecommendationDTO> getEventRecommendations(Long userId, Integer limit, Long requesterId, String requesterRole) {
        if (!userId.equals(requesterId) && !"ADMIN".equals(requesterRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own recommendations");
        }

        if (!bookingRepository.userExistsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        int recommendationLimit = limit == null ? 5 : limit;

        String cypher = """
            MATCH (target:User {userId: $userId})-[:ATTENDED]->(shared:Event)<-[:ATTENDED]-(similar:User)-[:ATTENDED]->(recommended:Event)
            WHERE NOT (target)-[:ATTENDED]->(recommended)
            RETURN recommended.eventId AS eventId,
                   recommended.name AS eventName,
                   recommended.category AS category,
                   count(similar) AS score
            ORDER BY score DESC
            LIMIT $limit
            """;

        try (var session = neo4jDriver.session()) {
            List<ProviderRecommendationDTO> recommendations = session.executeRead(tx ->
                    tx.run(cypher, Map.of("userId", userId, "limit", recommendationLimit))
                            .list(neo4jRecordAdapter::adapt)
            );

            List<Long> eventIds = recommendations.stream()
                    .map(ProviderRecommendationDTO::getProviderId)
                    .toList();

            if (eventIds.isEmpty()) {
                return recommendations;
            }

            Map<Long, Object[]> eventDetails = bookingRepository.findEventRecommendationDetails(eventIds)
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> row
                    ));

            for (ProviderRecommendationDTO recommendation : recommendations) {
                Object[] row = eventDetails.get(recommendation.getProviderId());
                if (row != null) {
                    recommendation.setName((String) row[1]);
                    recommendation.setSpecialty((String) row[2]);
                }
            }

            return recommendations;
        }
    }

    private void invalidateBookingCaches(Long bookingId) {
        cacheInvalidationService.invalidateCacheWildcard("booking-service::booking::" + bookingId);
        cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F9::*");
        cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("booking-service::S3-F12::*");
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

		if (bookingRepository.ticketsTableExists()) {
			bookingRepository.cancelValidTicketsByBookingId(bookingId);
		}

        Booking savedBooking = bookingRepository.save(booking);

        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", savedBooking.getId());
        payload.put("userId", savedBooking.getUserId());
        payload.put("eventId", savedBooking.getEventId());
        payload.put("status", savedBooking.getStatus().name());

        notifyObservers("BOOKING_CANCELLED", payload);

        invalidateBookingCaches(savedBooking.getId());
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
}

