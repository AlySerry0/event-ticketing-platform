package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.dto.*;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventCategory;
import com.team7.eventticketing.event.model.EventStatus;
import com.team7.eventticketing.event.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Event operations
 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Create a new event
     */
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
        return convertToDTO(savedEvent);
    }

    /**
     * Get event by ID
     */
    public EventDTO getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));
        return convertToDTO(event);
    }

    /**
     * Get all events
     */
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by category
     */
    public List<EventDTO> getEventsByCategory(String category) {
        EventCategory eventCategory = parseEventCategory(category);
        return eventRepository.findByCategory(eventCategory)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by status
     */
    public List<EventDTO> getEventsByStatus(String status) {
        EventStatus eventStatus = parseEventStatus(status);
        return eventRepository.findByStatus(eventStatus)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by category and status
     */
    public List<EventDTO> getEventsByCategoryAndStatus(String category, String status) {
        EventCategory eventCategory = parseEventCategory(category);
        EventStatus eventStatus = parseEventStatus(status);

        return eventRepository.findByCategoryAndStatus(eventCategory, eventStatus)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search events by name
     */
    public List<EventDTO> searchEventsByName(String name) {
        return eventRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search events by venue
     */
    public List<EventDTO> searchEventsByVenue(String venue) {
        return eventRepository.findByVenueContainingIgnoreCase(venue)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search events by optional category and required date range according to S2-F1.
     */
    public List<EventDTO> searchEvents(String category, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date and end date are required"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date"
            );
        }

        EventCategory eventCategory = null;
        if (category != null && !category.isBlank()) {
            eventCategory = parseEventCategory(category.trim());
        }

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(LocalTime.MAX);

        return eventRepository.searchByCategoryAndDateRange(eventCategory, rangeStart, rangeEnd)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get upcoming events
     */
    public List<EventDTO> getUpcomingEvents() {
        return eventRepository.findUpcomingEvents(LocalDateTime.now())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events between dates
     */
    public List<EventDTO> getEventsBetweenDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date and end date are required"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date must be before or equal to end date"
            );
        }

        return eventRepository.findEventsBetweenDates(startDate, endDate)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by minimum rating
     */
    public List<EventDTO> getEventsByMinimumRating(Double rating) {
        if (rating == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating is required"
            );
        }

        if (rating < 0 || rating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating must be between 0 and 5"
            );
        }

        return eventRepository.findByRatingGreaterThanEqual(rating)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update an event
     */
    @Transactional
    public EventDTO updateEvent(Long eventId, UpdateEventDTO eventDTO) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));


        if(eventDTO.getName() != null) {
            event.setName(eventDTO.getName());
        }
        if(eventDTO.getVenue() != null) {
            event.setVenue(eventDTO.getVenue());
        }
        if(eventDTO.getEventDate() != null) {
            event.setEventDate(eventDTO.getEventDate());
        }
        if(eventDTO.getCategory() != null) {
            EventCategory category = parseEventCategory(eventDTO.getCategory());
            event.setCategory(category);
        }
        if(eventDTO.getStatus() != null) {
            EventStatus status = parseEventStatus(eventDTO.getStatus());
            event.setStatus(status);
        }

        if (eventDTO.getDetails() != null) {
            event.setDetails(eventDTO.getDetails());
        }

        Event updatedEvent = eventRepository.save(event);
        return convertToDTO(updatedEvent);
    }

    /**
     * Update event status
     */
    @Transactional
    public void updateEventStatus(Long eventId, String status) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));

        EventStatus newStatus = parseEventStatus(status);

        if (newStatus == EventStatus.CANCELLED) {
            long activeBookings = eventRepository.countActiveBookingsForEvent(eventId);

            if (activeBookings > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot cancel event because it has active bookings"
                );
            }
        }

        event.setStatus(newStatus);
        eventRepository.save(event);
    }

    /**
     * Update event rating
     */
    @Transactional
    public EventDTO updateEventRating(Long eventId, Double newRating) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));

        if (newRating == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating is required"
            );
        }

        if (newRating < 0 || newRating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating must be between 0 and 5"
            );
        }

        int totalRatings = event.getTotalRatings();
        Double currentRating = event.getRating() == null ? 0.0 : event.getRating();

        double newAverageRating = (currentRating * totalRatings + newRating) / (totalRatings + 1);

        event.setRating(newAverageRating);
        event.setTotalRatings(totalRatings + 1);

        Event updatedEvent = eventRepository.save(event);
        return convertToDTO(updatedEvent);
    }

    /**
     * Delete an event
     */
    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));

        eventRepository.delete(event);
    }

    /**
     * Convert Event entity to EventDTO
     */
    private EventDTO convertToDTO(Event event) {
        List<EventSessionDTO> sessions = event.getEventSessions() == null ? null :
                event.getEventSessions().stream()
                        .map(session -> new EventSessionDTO(
                                session.getId(),
                                session.getTitle(),
                                session.getSpeaker(),
                                session.getStartTime(),
                                session.getEndTime(),
                                session.getCapacity(),
                                session.getVerified(),
                                session.getMetadata(),
                                session.getCreatedAt()
                        ))
                        .collect(Collectors.toList());
        return new EventDTO(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getEventDate(),
                event.getCategory().name(),
                event.getStatus().name(),
                event.getRating(),
                event.getTotalRatings(),
                event.getDetails(),
                event.getCreatedAt(),
                sessions
        );
    }

    /**
     * Parse and validate event category
     */
    private EventCategory parseEventCategory(String category) {
        try {
            return EventCategory.valueOf(category.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid event category: " + category
            );
        }
    }

    /**
     * Parse and validate event status
     */
    private EventStatus parseEventStatus(String status) {
        try {
            return EventStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid event status: " + status
            );
        }
    }

    public List<EventDTO> searchEventsByDetailAttribute(String key, String value, String status) {
        List<Event> events;

        if (status == null || status.isBlank()) {
            events = eventRepository.findByDetailAttribute(key, value);
        } else {
            String normalizedStatus = status.trim().toUpperCase();

            try {
                EventStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event status: " + status);
            }

            events = eventRepository.findByDetailAttributeAndStatus(key, value, normalizedStatus);
        }

        return events.stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<TopEventDTO> getTopRatedEvents(int limit) {

        List<Object[]> results = eventRepository.findTopRatedEvents(limit);

        return results.stream()
                .map(row -> new TopEventDTO(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        row[2] != null ? ((Number) row[2]).doubleValue() : 0.0,
                        ((Number) row[3]).longValue()
                ))
                .toList();
    }

    /**
     * Rate an event after attendance according to S2-F7
     */
    @Transactional
    public void rateEventAfterAttendance(Long eventId, Long bookingId, Integer rating) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));

        if (bookingId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking ID is required"
            );
        }

        if (rating == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating is required"
            );
        }

        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating must be between 1 and 5"
            );
        }

        long bookingExists = eventRepository.countBookingById(bookingId);
        if (bookingExists == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking not found with id: " + bookingId
            );
        }

        long validCompletedBooking = eventRepository.countCompletedBookingForEvent(bookingId, eventId);
        if (validCompletedBooking == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking must belong to this event and be COMPLETED"
            );
        }

        int currentTotalRatings = event.getTotalRatings() == null ? 0 : event.getTotalRatings();
        double currentAverageRating = event.getRating() == null ? 0.0 : event.getRating();

        double newAverageRating =
                (currentAverageRating * currentTotalRatings + rating) / (currentTotalRatings + 1);

        event.setRating(newAverageRating);
        event.setTotalRatings(currentTotalRatings + 1);

        eventRepository.save(event);
    }
}
