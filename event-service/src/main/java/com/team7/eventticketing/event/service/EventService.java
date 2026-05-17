package com.team7.eventticketing.event.service;

import com.team7.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team7.eventticketing.contracts.dto.VenueCoordsDTO;
import com.team7.eventticketing.event.adapter.ElasticsearchHitAdapter;
import com.team7.eventticketing.event.adapter.ObjectArrayDtoAdapter;
import com.team7.eventticketing.event.dto.*;
import com.team7.eventticketing.event.elasticsearch.EventSearchDocument;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventCategory;
import com.team7.eventticketing.event.model.EventSession;
import com.team7.eventticketing.event.model.EventStatus;
import com.team7.eventticketing.event.observer.EntityObserver;
import com.team7.eventticketing.event.observer.MongoEventLogger;
import com.team7.eventticketing.event.repository.EventRepository;
import com.team7.eventticketing.event.util.CacheInvalidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team7.eventticketing.contracts.events.EventRatedEvent;
import com.team7.eventticketing.contracts.events.EventStatusChangedEvent;
import com.team7.eventticketing.event.messaging.publisher.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import com.team7.eventticketing.contracts.dto.BookingDTO;
import com.team7.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team7.eventticketing.contracts.feign.BookingServiceClient;
import feign.FeignException;

@Service
@Transactional(readOnly = true)
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    // -----------------------------------------------------------------------
    // Observer registry (classical GoF — not Spring ApplicationEventPublisher)
    // -----------------------------------------------------------------------
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final  EventIndexService eventIndexService;  // needed to trigger re-indexing on updates
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchHitAdapter elasticsearchHitAdapter;
    private final EventPublisher eventPublisher;
    private final BookingServiceClient bookingServiceClient;
//    @Autowired
//    private EventService self;

    public void register(EntityObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(action, payload);
        }
    }

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------
    private final EventRepository eventRepository;
    private final CacheInvalidationService cacheInvalidationService;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();
    private EventCacheService eventCacheService;

    /**
     * Constructor — MongoEventLogger is injected by Spring and registered
     * as the single observer for this service.
     */
    public EventService(EventRepository eventRepository,
                        MongoEventLogger mongoEventLogger,
                        EventIndexService eventIndexService,
                        ElasticsearchOperations elasticsearchOperations,
                        ElasticsearchHitAdapter elasticsearchHitAdapter,
                        CacheInvalidationService cacheInvalidationService,
                        EventCacheService eventCacheService,
                        EventPublisher eventPublisher,
                        BookingServiceClient bookingServiceClient) {
        this.eventRepository = eventRepository;
        this.eventIndexService = eventIndexService;
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchHitAdapter = elasticsearchHitAdapter;
        this.register(mongoEventLogger);
        this.cacheInvalidationService = cacheInvalidationService;
        this.eventCacheService = eventCacheService;
        this.eventPublisher = eventPublisher;
        this.bookingServiceClient = bookingServiceClient;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> buildPayload(Long eventId, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        if (eventId != null) payload.put("eventId", eventId);
        if (extra != null) payload.putAll(extra);
        return payload;
    }

    /**
     * Invalidates the entity detail cache + all feature caches that involve events.
     * Called after every write that touches an Event row.
     * Uses over-invalidation (§4.4.6) — correctness beats hit ratio.
     */
    private void invalidateEventCaches(Long eventId) {
        // Entity detail cache (CRUD GET /api/events/{id}) — 15 min TTL
        cacheInvalidationService.invalidateCacheWildcard("event-service::event::" + eventId);

        // Feature caches — all wildcard because params vary per caller
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F1::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F3::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F9::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F10::*");
        // S2-F12 dashboard is keyed by eventId so we can be precise
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::" + eventId + "::*");
    }

    /**
     * Invalidates only session-related caches.
     * Called after writes that touch EventSession rows but not the Event itself.
     */
    private void invalidateSessionCaches(Long sessionId) {
        cacheInvalidationService.invalidateCacheWildcard("event-service::event-session::" + sessionId + "::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F9::*");
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Transactional
    public EventDTO createEvent(CreateEventDTO eventDTO) {
        EventCategory category = parseEventCategory(eventDTO.getCategory());
        EventStatus status = parseEventStatus(eventDTO.getStatus());

        Event event = new Event(
                eventDTO.getName(),
                eventDTO.getVenue(),
                eventDTO.getEventDate(),
                category,
                status
        );

        if (eventDTO.getDetails() != null) {
            event.setDetails(eventDTO.getDetails());
        }

        Event savedEvent = eventRepository.save(event);
        eventIndexService.indexEvent(savedEvent.getId(), "auto_crud_create");
        EventDTO result = convertToDTO(savedEvent);

        // No entity detail to invalidate (new entity has no cached detail yet).
        // But bust search/filter caches because result lists now change.
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F1::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F9::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F10::*");

        Map<String, Object> extra = new HashMap<>();
        extra.put("name", savedEvent.getName());
        extra.put("category", savedEvent.getCategory().name());
        notifyObservers("EVENT_CREATED", buildPayload(savedEvent.getId(), extra));

        return result;
    }

    // -----------------------------------------------------------------------
    // Read — CRUD detail (15 min)
    // -----------------------------------------------------------------------

    /**
     * GET /api/events/{id} — cached per §4.4.2 (only get-by-ID is cached, not list)
     */
    @Cacheable(value = "event", key = "#eventId")
    public EventDTO getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));
        return convertToDTO(event);
    }

    /**
     * GET /api/events — NOT cached (list endpoints are never cached per §4.4.2)
     */
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll()
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> getEventsByCategory(String category) {
        EventCategory eventCategory = parseEventCategory(category);
        return eventRepository.findByCategory(eventCategory)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> getEventsByStatus(String status) {
        EventStatus eventStatus = parseEventStatus(status);
        return eventRepository.findByStatus(eventStatus)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> getEventsByCategoryAndStatus(String category, String status) {
        EventCategory eventCategory = parseEventCategory(category);
        EventStatus eventStatus = parseEventStatus(status);
        return eventRepository.findByCategoryAndStatus(eventCategory, eventStatus)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> searchEventsByName(String name) {
        return eventRepository.findByNameContainingIgnoreCase(name)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> searchEventsByVenue(String venue) {
        return eventRepository.findByVenueContainingIgnoreCase(venue)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> getUpcomingEvents() {
        return eventRepository.findUpcomingEvents(LocalDateTime.now())
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> getEventsBetweenDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }
        return eventRepository.findEventsBetweenDates(startDate, endDate)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<EventDTO> getEventsByMinimumRating(Double rating) {
        if (rating == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating is required");
        }
        if (rating < 0 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rating must be between 0 and 5");
        }
        return eventRepository.findByRatingGreaterThanEqual(rating)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // S2-F1 — Search by category + date range (cached 5 min)
    // -----------------------------------------------------------------------

    @Cacheable(value = "S2-F1", key = "#category + '_' + #startDate + '_' + #endDate")
    public List<EventDTO> searchEvents(String category, LocalDate startDate, LocalDate endDate) {

        // Only one date provided — reject
        if ((startDate == null) != (endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both startDate and endDate must be provided together, or both omitted");
        }

        // Both dates provided — validate order
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        // Parse category — null means no category filter
        EventCategory eventCategory = null;
        if (category != null && !category.isBlank()) {
            eventCategory = parseEventCategory(category.trim());
        }

        // 4 cases
        List<Event> events;

        if (eventCategory != null && startDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime   = endDate.atTime(LocalTime.MAX);
            events = eventRepository.findByCategoryAndEventDateBetweenOrderByEventDateAsc(
                    eventCategory, startDateTime, endDateTime);

        } else if (eventCategory == null && startDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime   = endDate.atTime(LocalTime.MAX);
            events = eventRepository.findByEventDateBetweenOrderByEventDateAsc(
                    startDateTime, endDateTime);

        } else if (eventCategory != null) {

            events = eventRepository.findByCategory(eventCategory);

        } else {

            events = eventRepository.findAllByOrderByEventDateAsc();
        }

        return events.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    // -----------------------------------------------------------------------
    // S2-F2 — JSONB partial update (write — invalidate)
    // -----------------------------------------------------------------------

    @Transactional
    public EventDTO updateEvent(Long eventId, UpdateEventDTO eventDTO) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        if (eventDTO.getName() != null)      event.setName(eventDTO.getName());
        if (eventDTO.getVenue() != null)     event.setVenue(eventDTO.getVenue());
        if (eventDTO.getEventDate() != null) event.setEventDate(eventDTO.getEventDate());
        if (eventDTO.getCategory() != null)  event.setCategory(parseEventCategory(eventDTO.getCategory()));
        if (eventDTO.getStatus() != null)    event.setStatus(parseEventStatus(eventDTO.getStatus()));
        if (eventDTO.getDetails() != null)   event.setDetails(eventDTO.getDetails());

        Event updatedEvent = eventRepository.save(event);
        eventIndexService.indexEvent(updatedEvent.getId(), "auto_crud_update");
        EventDTO result = convertToDTO(updatedEvent);

        invalidateEventCaches(eventId);

        Map<String, Object> extra = new HashMap<>();
        extra.put("name", updatedEvent.getName());
        notifyObservers("EVENT_UPDATED", buildPayload(eventId, extra));

        return result;
    }

    @Transactional
    public EventDTO updateEventDetails(Long eventId, Map<String, Object> detailsUpdate) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        if (detailsUpdate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Details update body is required");
        }

        Map<String, Object> mergedDetails = new LinkedHashMap<>();
        if (event.getDetails() != null) mergedDetails.putAll(event.getDetails());
        mergedDetails.putAll(detailsUpdate);
        event.setDetails(mergedDetails);

        Event updatedEvent = eventRepository.save(event);
        EventDTO result = convertToDTO(updatedEvent);

        // S2-F2 is a write — invalidate
        invalidateEventCaches(eventId);

        Map<String, Object> extra = new HashMap<>();
        extra.put("updatedKeys", new ArrayList<>(detailsUpdate.keySet()));
        notifyObservers("DETAILS_UPDATED", buildPayload(eventId, extra));

        return result;
    }

    // -----------------------------------------------------------------------
    // S2-F3 — Revenue summary (cached 10 min)
    // -----------------------------------------------------------------------

    @Cacheable(value = "S2-F3", key = "#eventId + '_' + #startDate + '_' + #endDate")
    public EventRevenueDTO getEventRevenueSummary(Long eventId, LocalDate startDate, LocalDate endDate) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date and end date are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        try {
            log.info("Calling booking-service.getEventRevenue for eventId={} startDate={} endDate={}",
                    eventId, startDate, endDate);

            EventBookingRevenueDTO revenue = bookingServiceClient.getEventRevenue(
                    eventId,
                    startDate.toString(),
                    endDate.toString()
            );

            log.info("booking-service.getEventRevenue returned successfully for eventId={}", eventId);

            return EventRevenueDTO.builder()
                    .eventId(event.getId())
                    .name(event.getName())
                    .totalBookings(revenue.totalBookings())
                    .totalRevenue(revenue.totalRevenue() == null ? 0.0 : revenue.totalRevenue().doubleValue())
                    .averageBookingAmount(revenue.averageBookingAmount() == null ? 0.0 : revenue.averageBookingAmount().doubleValue())
                    .build();

        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking revenue not found for event id: " + eventId
            );

        } catch (FeignException e) {
            log.warn("booking-service unavailable while getting revenue for event {}: {}",
                    eventId, e.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Booking service temporarily unavailable"
            );
        }
    }
    // -----------------------------------------------------------------------
    // S2-F4 — Status update (write — invalidate)
    // -----------------------------------------------------------------------

    @Transactional
    public void updateEventStatus(Long eventId, String status) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        EventStatus newStatus = parseEventStatus(status);

        if (newStatus == EventStatus.CANCELLED) {
            int activeBookings;

            try {
                log.info("Calling booking-service.getEventActiveBookingCount for eventId={}", eventId);

                activeBookings = bookingServiceClient.getEventActiveBookingCount(eventId);

                log.info("booking-service.getEventActiveBookingCount returned {} for eventId={}",
                        activeBookings, eventId);

            } catch (FeignException e) {
                log.warn("booking-service unavailable while checking active bookings for event {}: {}",
                        eventId, e.getMessage());

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Booking service temporarily unavailable"
                );
            }

            if (activeBookings > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot cancel event because it has active bookings");
            }
        }

        String oldStatus = event.getStatus().name();
        event.setStatus(newStatus);
        eventRepository.save(event);

        // S2-F4 is a write — invalidate
        invalidateEventCaches(eventId);

        Map<String, Object> extra = new HashMap<>();
        extra.put("oldStatus", oldStatus);
        extra.put("newStatus", newStatus.name());
        notifyObservers("STATUS_CHANGED", buildPayload(eventId, extra));
        eventPublisher.publishStatusChanged(
                new EventStatusChangedEvent(
                        event.getId(),
                        event.getName(),
                        oldStatus,
                        newStatus.name(),
                        LocalDateTime.now()
                )
        );
    }

    // -----------------------------------------------------------------------
    // S2-F5 — JSONB attribute filter (cached 5 min)
    // -----------------------------------------------------------------------

    @Cacheable(value = "S2-F5", key = "#key + '_' + #value + '_' + #status")
    public List<EventDTO> searchEventsByDetailAttribute(String key, String value, String status) {
        List<Event> events;
        if (status == null || status.isBlank()) {
            events = eventRepository.findByDetailAttribute(key, value);
        } else {
            String normalizedStatus = status.trim().toUpperCase();
            try { EventStatus.valueOf(normalizedStatus); }
            catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid event status: " + status);
            }
            events = eventRepository.findByDetailAttributeAndStatus(key, value, normalizedStatus);
        }
        return events.stream().map(this::convertToDTO).toList();
    }

    // -----------------------------------------------------------------------
    // S2-F6 — Top rated report (cached 10 min)
    // -----------------------------------------------------------------------

    @Cacheable(value = "S2-F6", key = "#limit")
    public List<TopEventDTO> getTopRatedEvents(int limit) {
        List<Object[]> results = eventRepository.findTopRatedEvents(limit);
        return results.stream()
                .map(objectArrayDtoAdapter::toTopEventDTO)
                .toList();
    }

    // -----------------------------------------------------------------------
    // S2-F7 — Rate after attendance (write — invalidate)
    // -----------------------------------------------------------------------

    @Transactional
    public void rateEventAfterAttendance(Long eventId, Long bookingId, Integer rating) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        if (bookingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking ID is required");
        }
        if (rating == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating is required");
        }
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rating must be between 1 and 5");
        }

        BookingDTO booking;

        try {
            log.info("Calling booking-service.getBooking for bookingId={}", bookingId);

            booking = bookingServiceClient.getBooking(bookingId);

            log.info("booking-service.getBooking returned successfully for bookingId={}", bookingId);

        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking not found with id: " + bookingId
            );

        } catch (FeignException e) {
            log.warn("booking-service unavailable while validating booking {}: {}",
                    bookingId, e.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Booking service temporarily unavailable"
            );
        }

        if (!eventId.equals(booking.eventId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking must belong to this event"
            );
        }

        if (booking.status() == null || !"COMPLETED".equalsIgnoreCase(booking.status().name())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking must be COMPLETED"
            );
        }

        int currentTotal = event.getTotalRatings() == null ? 0 : event.getTotalRatings();
        double currentAvg = event.getRating() == null ? 0.0 : event.getRating();
        double newAvg = (currentAvg * currentTotal + rating) / (currentTotal + 1);

        event.setRating(newAvg);
        event.setTotalRatings(currentTotal + 1);
        eventRepository.save(event);

        // Rating changes event detail + top-rated list + dashboard averageRating
        cacheInvalidationService.invalidateCacheWildcard("event-service::event::" + eventId + "::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::" + eventId + "::*");

        Map<String, Object> extra = new HashMap<>();
        extra.put("bookingId", bookingId);
        extra.put("rating", rating);
        extra.put("newAverageRating", newAvg);
        notifyObservers("RATED", buildPayload(eventId, extra));
        eventPublisher.publishRated(
                new EventRatedEvent(
                        event.getId(),
                        bookingId,
                        rating,
                        newAvg,
                        event.getTotalRatings(),
                        LocalDateTime.now()
                )
        );
    }

    @Transactional
    public EventDTO updateEventRating(Long eventId, Double newRating) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        if (newRating == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating is required");
        }
        if (newRating < 0 || newRating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rating must be between 0 and 5");
        }

        int totalRatings = event.getTotalRatings();
        double currentRating = event.getRating() == null ? 0.0 : event.getRating();
        double newAvg = (currentRating * totalRatings + newRating) / (totalRatings + 1);

        event.setRating(newAvg);
        event.setTotalRatings(totalRatings + 1);
        Event updatedEvent = eventRepository.save(event);

        cacheInvalidationService.invalidateCacheWildcard("event-service::event::" + eventId + "::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::" + eventId + "::*");

        Map<String, Object> extra = new HashMap<>();
        extra.put("rating", newRating);
        notifyObservers("RATED", buildPayload(eventId, extra));

        return convertToDTO(updatedEvent);
    }

    // -----------------------------------------------------------------------
    // S2-F9 — Unverified sessions report (cached 10 min)
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // S2-F12 — Event Performance Dashboard
    // IMPORTANT: MongoDB log runs on EVERY call including cache hits.
    // Split into cached inner method + public wrapper per spec §10.2.3 step g.
    // -----------------------------------------------------------------------

    public EventDashboardDTO getEventDashboard(Long eventId) {
        long start = System.currentTimeMillis();

        try {
            MDC.put("eventId", eventId.toString());

            log.info("Received S2-F12 dashboard request for eventId={}", eventId);

            EventDashboardDTO result = eventCacheService.getEventDashboardCached(eventId);

            notifyObservers("DASHBOARD_VIEWED", buildPayload(eventId, Collections.emptyMap()));

            long elapsed = System.currentTimeMillis() - start;

            if (elapsed > 1000) {
                log.warn("Slow S2-F12 dashboard took {}ms for eventId={}", elapsed, eventId);
            }

            log.info("Returning S2-F12 dashboard for eventId={}", eventId);

            return result;

        } finally {
            MDC.remove("eventId");
        }
    }

    // -----------------------------------------------------------------------
    // Delete (write — invalidate)
    // -----------------------------------------------------------------------

    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        eventIndexService.removeFromIndex(eventId, event.getName());
        eventRepository.delete(event);

        invalidateEventCaches(eventId);

        Map<String, Object> extra = new HashMap<>();
        extra.put("name", event.getName());
        extra.put("source", "auto_crud_delete");
        notifyObservers("EVENT_DELETED", buildPayload(eventId, extra));
    }

    @Cacheable(value = "S2-F10", key = "#query + '_' + #category + '_' + #venue + '_' + #status + '_' + #startDate + '_' + #endDate + '_' + #minRating + '_' + #maxRating")
    public List<EventDTO> searchEventsFullText(
            String query, String category, String venue, String status,
            LocalDate startDate, LocalDate endDate, Double minRating, Double maxRating) {

        // 🔹 Validate inputs
        if (minRating != null && (minRating < 0 || minRating > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minRating must be between 0 and 5");
        }
        if (maxRating != null && (maxRating < 0 || maxRating > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "maxRating must be between 0 and 5");
        }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minRating must be <= maxRating");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before or equal to endDate");
        }

        // 🔹 Build filters
        List<String> filters = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            filters.add("""
            { "term": { "category": "%s" } }
        """.formatted(category));
        }

        if (venue != null && !venue.isBlank()) {
            filters.add("""
            { "term": { "venue.keyword": "%s" } }
        """.formatted(venue));
        }

        if (status != null && !status.isBlank()) {
            filters.add("""
            { "term": { "status": "%s" } }
        """.formatted(status));
        }

        if (minRating != null || maxRating != null) {
            StringBuilder range = new StringBuilder();
            if (minRating != null) {
                range.append("\"gte\": ").append(minRating);
            }
            if (maxRating != null) {
                if (range.length() > 0) range.append(",");
                range.append("\"lte\": ").append(maxRating);
            }

            filters.add("""
            { "range": { "rating": { %s } } }
        """.formatted(range));
        }

        if (startDate != null || endDate != null) {
            StringBuilder range = new StringBuilder();
            if (startDate != null) {
                range.append("\"gte\": \"").append(startDate.atStartOfDay()).append("\"");
            }
            if (endDate != null) {
                if (range.length() > 0) range.append(",");
                range.append("\"lte\": \"").append(endDate.atTime(LocalTime.MAX)).append("\"");
            }

            filters.add("""
            { "range": { "eventDate": { %s } } }
        """.formatted(range));
        }

        // 🔹 Build MUST (only if query exists)
        String mustClause = "";

        if (query != null && !query.isBlank()) {
            mustClause = """
        "must": [
          {
            "multi_match": {
              "query": "%s",
              "fields": ["name^3", "venue^2", "details.description"],
              "fuzziness": "AUTO"
            }
          }
        ],
        """.formatted(query.replace("\"", "\\\""));
        }

        // 🔹 Final query
        String jsonQuery = """
    {
      "bool": {
        %s
        "filter": [
          %s
        ]
      }
    }
    """.formatted(
                mustClause,
                String.join(",", filters)
        );

        // 🔹 Execute using StringQuery
        StringQuery searchQuery = new StringQuery(jsonQuery);

        SearchHits<EventSearchDocument> searchHits =
                elasticsearchOperations.search(searchQuery, EventSearchDocument.class);

        return searchHits.getSearchHits().stream()
                .map(elasticsearchHitAdapter::adapt)
                .collect(Collectors.toList());
    }

    public AvgCapacityDTO calculateAvgCapacity(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + id));

        double avg = event.getEventSessions().stream()
                .mapToDouble(EventSession::getCapacity)
                .average()
                .orElse(0.0);

        return new AvgCapacityDTO(avg);
    }

    public VenueCoordsDTO getVenueCoords(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + id));

        // Logic to extract from Event.details JSONB
        Map<String, Object> details = event.getDetails();
        if (details == null || !details.containsKey("venueLat") || !details.containsKey("venueLon")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Venue coordinates not found in event details for event id: " + id);
        }
        Double lat = (Double) details.get("venueLat");
        Double lon = (Double) details.get("venueLon");

        return new VenueCoordsDTO(lat, lon);
    }

    // -----------------------------------------------------------------------
    // Conversion helper
    // -----------------------------------------------------------------------

    public EventDTO convertToDTO(Event event) {
        List<EventSessionDTO> sessions = event.getEventSessions() == null ? null :
                event.getEventSessions().stream()
                        .map(s -> new EventSessionDTO(
                                s.getId(), s.getTitle(), s.getSpeaker(),
                                s.getStartTime(), s.getEndTime(), s.getCapacity(),
                                s.getVerified(), s.getMetadata(), s.getCreatedAt()))
                        .collect(Collectors.toList());

        return new EventDTO(
                event.getId(), event.getName(), event.getVenue(), event.getEventDate(),
                event.getCategory().name(), event.getStatus().name(), event.getRating(),
                event.getTotalRatings(), event.getDetails(), event.getCreatedAt(), sessions);
    }

    // -----------------------------------------------------------------------
    // Enum parsers
    // -----------------------------------------------------------------------

    private EventCategory parseEventCategory(String category) {
        try {
            return EventCategory.valueOf(category.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid event category: " + category);
        }
    }

    private EventStatus parseEventStatus(String status) {
        try {
            return EventStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid event status: " + status);
        }
    }

    public void invalidateRevenueCacheForEvent(Long eventId) {
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F3::" + eventId + "_*");
    }

    public void invalidateDashboardCacheForEvent(Long eventId) {
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::" + eventId + "::*");
    }

    public void invalidateBookingDependentCaches(Long eventId) {
        invalidateRevenueCacheForEvent(eventId);
        invalidateDashboardCacheForEvent(eventId);
    }
}