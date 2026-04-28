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

/**
 * Service for Event operations.
 *
 * Observer Pattern: maintains a list of EntityObserver instances.
 * On every write (M1 features + CRUD), calls notifyObservers(action, payload)
 * AFTER the PostgreSQL transaction commits — Mongo failure never rolls back PG.
 *
 * Adapter Pattern: Object[] rows from native SQL queries are converted to DTOs
 * via EventRevenueAdapter (S2-F3) and TopEventAdapter (S2-F6) instead of
 * inline casting inside this service.
 */
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

    // Adapters (Adapter Pattern)
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();

    /**
     * Constructor — MongoEventLogger is injected by Spring and registered
     * as the single observer for this service.
     */
    public EventService(EventRepository eventRepository, MongoEventLogger mongoEventLogger, EventIndexService eventIndexService, ElasticsearchOperations elasticsearchOperations, ElasticsearchHitAdapter elasticsearchHitAdapter) {
        this.eventRepository = eventRepository;
        this.eventIndexService = eventIndexService;
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchHitAdapter = elasticsearchHitAdapter;
        this.register(mongoEventLogger);
    }

    // -----------------------------------------------------------------------
    // Helpers shared by both Observer calls and payload builders
    // -----------------------------------------------------------------------

    /** Builds the standard payload map that MongoEventLogger expects. */
    private Map<String, Object> buildPayload(Long eventId, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        if (eventId != null) payload.put("eventId", eventId);
        if (extra != null) payload.putAll(extra);
        return payload;
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

        // Observer notification — EVENT_CREATED
        Map<String, Object> extra = new HashMap<>();
        extra.put("name", savedEvent.getName());
        extra.put("category", savedEvent.getCategory().name());
        notifyObservers("EVENT_CREATED", buildPayload(savedEvent.getId(), extra));

        return result;
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    public EventDTO getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));
        return convertToDTO(event);
    }

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

    /** S2-F1 — search by optional category and required date range. */
    public List<EventDTO> searchEvents(String category, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date");
        }

        EventCategory eventCategory = null;
        if (category != null && !category.isBlank()) {
            eventCategory = parseEventCategory(category.trim());
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Event> events = (eventCategory == null)
                ? eventRepository.findByEventDateBetweenOrderByEventDateAsc(startDateTime, endDateTime)
                : eventRepository.findByCategoryAndEventDateBetweenOrderByEventDateAsc(
                eventCategory, startDateTime, endDateTime);

        return events.stream().map(this::convertToDTO).collect(Collectors.toList());
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
    // Update — S2-F2
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

        // Observer notification — EVENT_UPDATED
        Map<String, Object> extra = new HashMap<>();
        extra.put("name", updatedEvent.getName());
        notifyObservers("EVENT_UPDATED", buildPayload(eventId, extra));

        return result;
    }

    /** S2-F2 — JSONB partial update. */
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

        // Observer notification — DETAILS_UPDATED (S2-F2)
        Map<String, Object> extra = new HashMap<>();
        extra.put("updatedKeys", new ArrayList<>(detailsUpdate.keySet()));
        notifyObservers("DETAILS_UPDATED", buildPayload(eventId, extra));

        return result;
    }

    // -----------------------------------------------------------------------
    // S2-F3 — Revenue summary (uses Adapter)
    // -----------------------------------------------------------------------

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

        // Adapter Pattern — delegates Object[] → EventRevenueDTO conversion
        return objectArrayDtoAdapter.toEventRevenueDTO(
                row,
                event.getId(),
                event.getName()
        );
    }

    // -----------------------------------------------------------------------
    // S2-F4 — Status update
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

        // Observer notification — STATUS_CHANGED (S2-F4)
        Map<String, Object> extra = new HashMap<>();
        extra.put("oldStatus", oldStatus);
        extra.put("newStatus", newStatus.name());
        notifyObservers("STATUS_CHANGED", buildPayload(eventId, extra));
    }

    // -----------------------------------------------------------------------
    // S2-F5 — JSONB attribute search (read — no observer needed)
    // -----------------------------------------------------------------------

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
    // S2-F6 — Top rated (uses Adapter)
    // -----------------------------------------------------------------------

    public List<TopEventDTO> getTopRatedEvents(int limit) {
        List<Object[]> results = eventRepository.findTopRatedEvents(limit);
        // Adapter Pattern — delegates Object[] → TopEventDTO conversion
        return results.stream()
                .map(objectArrayDtoAdapter::toTopEventDTO)
                .toList();
    }

    // -----------------------------------------------------------------------
    // S2-F7 — Rate after attendance
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

        // Observer notification — RATED (S2-F7)
        Map<String, Object> extra = new HashMap<>();
        extra.put("bookingId", bookingId);
        extra.put("rating", rating);
        extra.put("newAverageRating", newAvg);
        notifyObservers("RATED", buildPayload(eventId, extra));
    }

    // -----------------------------------------------------------------------
    // S2-F7 (legacy overload kept for compatibility)
    // -----------------------------------------------------------------------

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

        Map<String, Object> extra = new HashMap<>();
        extra.put("rating", newRating);
        notifyObservers("RATED", buildPayload(eventId, extra));

        return convertToDTO(updatedEvent);
    }



    // -----------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------

    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        eventIndexService.removeFromIndex(eventId, event.getName()); // ES only
        eventRepository.delete(event);

        // Observer notification — EVENT_DELETED
        Map<String, Object> extra = new HashMap<>();
        extra.put("name", event.getName());
        extra.put("source", "auto_crud_delete");
        notifyObservers("EVENT_DELETED", buildPayload(eventId, extra));
    }


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