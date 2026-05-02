package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.adapter.ElasticsearchHitAdapter;
import com.team7.eventticketing.event.adapter.ObjectArrayDtoAdapter;
import com.team7.eventticketing.event.dto.*;
import com.team7.eventticketing.event.elasticsearch.EventSearchDocument;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventCategory;
import com.team7.eventticketing.event.model.EventStatus;
import com.team7.eventticketing.event.observer.EntityObserver;
import com.team7.eventticketing.event.observer.MongoEventLogger;
import com.team7.eventticketing.event.repository.EventRepository;
import com.team7.eventticketing.event.util.CacheInvalidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EventService {

    // -----------------------------------------------------------------------
    // Observer registry (classical GoF — not Spring ApplicationEventPublisher)
    // -----------------------------------------------------------------------
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final  EventIndexService eventIndexService;  // needed to trigger re-indexing on updates
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchHitAdapter elasticsearchHitAdapter;
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
    public EventService(EventRepository eventRepository, MongoEventLogger mongoEventLogger, EventIndexService eventIndexService, ElasticsearchOperations elasticsearchOperations, ElasticsearchHitAdapter elasticsearchHitAdapter,
CacheInvalidationService cacheInvalidationService, EventCacheService eventCacheService) {
        this.eventRepository = eventRepository;
        this.eventIndexService = eventIndexService;
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchHitAdapter = elasticsearchHitAdapter;
        this.register(mongoEventLogger);
        this.cacheInvalidationService = cacheInvalidationService;
        this.eventCacheService = eventCacheService;
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

        // Only validate if BOTH dates are provided — if only one is given, reject
        if ((startDate == null) != (endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both startDate and endDate must be provided together, or both omitted");
        }

        // If both provided, validate order
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        // Convert to LocalDateTime — null stays null, query handles it
        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime   = (endDate != null)   ? endDate.atTime(LocalTime.MAX) : null;

        // Category — null means no filter, pass raw string to query
        String categoryStr = (category != null && !category.isBlank())
                ? parseEventCategory(category.trim()).name()
                : null;

        return eventRepository.searchEventsFlexible(categoryStr, startDateTime, endDateTime)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(LocalTime.MAX);

        Object rawResult = eventRepository.findEventRevenueSummary(eventId, rangeStart, rangeEnd);

        Object[] row;
        if (rawResult == null) {
            row = new Object[]{0L, 0.0, 0.0};
        } else if (rawResult instanceof Object[] arr) {
            row = (arr.length > 0 && arr[0] instanceof Object[]) ? (Object[]) arr[0] : arr;
        } else {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected revenue query result format");
        }

        return objectArrayDtoAdapter.toEventRevenueDTO(row, event.getId(), event.getName());
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
            long activeBookings = eventRepository.countActiveBookingsForEvent(eventId);
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

        if (eventRepository.countBookingById(bookingId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Booking not found with id: " + bookingId);
        }
        if (eventRepository.countCompletedBookingForEvent(bookingId, eventId) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking must belong to this event and be COMPLETED");
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
        EventDashboardDTO result = eventCacheService.getEventDashboardCached(eventId);
        notifyObservers("DASHBOARD_VIEWED", buildPayload(eventId, Collections.emptyMap()));
        return result;
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

    @Cacheable(value = "S2-F10", key = "#query + '_' + #category + '_' + #venue + #status + '_' + #startDate + '_' + #endDate + '_' + #minRating + '_' + #maxRating")
    public List<EventDTO> searchEventsFullText(
            String query, String category, String venue, String status,
            LocalDate startDate, LocalDate endDate, Double minRating, Double maxRating) {

        Criteria criteria = new Criteria();

        // b) Full-text search on query against name, description, and venue
        if (query != null && !query.isBlank()) {
            criteria.subCriteria(new Criteria("name").contains(query)
                    .or(new Criteria("description").contains(query))
                    .or(new Criteria("venue").contains(query)));
        }

        // c) Optional Exact Match & Range Filters
        if (category != null && !category.isBlank()) {
            criteria.and(new Criteria("category").is(category));
        }
        if (venue != null && !venue.isBlank()) {
            criteria.and(new Criteria("venue").is(venue));
        }
        if (status != null && !status.isBlank()) {
            criteria.and(new Criteria("status").is(status));
        }

        // Rating Range Filter
        if (minRating != null) {
            criteria.and(new Criteria("rating").greaterThanEqual(minRating));
        }
        if (maxRating != null) {
            criteria.and(new Criteria("rating").lessThanEqual(maxRating));
        }

        // Date Range Filter (Expanding to cover the entire day constraints)
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = new Criteria("eventDate");
            if (startDate != null) {
                dateCriteria.greaterThanEqual(startDate.atStartOfDay());
            }
            if (endDate != null) {
                dateCriteria.lessThanEqual(endDate.atTime(LocalTime.MAX));
            }
            criteria.and(dateCriteria);
        }

        CriteriaQuery criteriaQuery = new CriteriaQuery(criteria);

        // Execute query
        SearchHits<EventSearchDocument> searchHits = elasticsearchOperations.search(criteriaQuery, EventSearchDocument.class);

        // d) Map hits using the required Adapter Pattern
        return searchHits.getSearchHits().stream()
                .map(elasticsearchHitAdapter::adapt)
                .collect(Collectors.toList());
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
}