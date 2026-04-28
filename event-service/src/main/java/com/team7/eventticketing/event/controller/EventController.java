package com.team7.eventticketing.event.controller;

import com.team7.eventticketing.event.dto.CreateEventDTO;
import com.team7.eventticketing.event.dto.EventDashboardDTO;
import com.team7.eventticketing.event.dto.EventDTO;
import com.team7.eventticketing.event.dto.EventRevenueDTO;
import com.team7.eventticketing.event.dto.TopEventDTO;
import com.team7.eventticketing.event.dto.UpdateEventDTO;
import com.team7.eventticketing.event.service.EventIndexService;
import com.team7.eventticketing.event.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Event operations
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventIndexService eventIndexService;

    public EventController(EventService eventService, EventIndexService eventIndexService) {
        this.eventService = eventService;
        this.eventIndexService = eventIndexService;
    }

    /**
     * Create a new event
     * POST /api/events
     */
    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@RequestBody CreateEventDTO request) {
        EventDTO event = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    /**
     * Get event by ID
     * GET /api/events/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        EventDTO event = eventService.getEventById(id);
        return ResponseEntity.ok(event);
    }

    /**
     * Get all events
     * GET /api/events
     */
    @GetMapping
    public ResponseEntity<List<EventDTO>> getAllEvents() {
        List<EventDTO> events = eventService.getAllEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Get events by category
     * GET /api/events/category/{category}
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<EventDTO>> getEventsByCategory(@PathVariable String category) {
        List<EventDTO> events = eventService.getEventsByCategory(category);
        return ResponseEntity.ok(events);
    }

    /**
     * Get events by status
     * GET /api/events/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<EventDTO>> getEventsByStatus(@PathVariable String status) {
        List<EventDTO> events = eventService.getEventsByStatus(status);
        return ResponseEntity.ok(events);
    }

    /**
     * Get events by category and status
     * GET /api/events/category/{category}/status/{status}
     */
    @GetMapping("/category/{category}/status/{status}")
    public ResponseEntity<List<EventDTO>> getEventsByCategoryAndStatus(
            @PathVariable String category,
            @PathVariable String status) {
        List<EventDTO> events = eventService.getEventsByCategoryAndStatus(category, status);
        return ResponseEntity.ok(events);
    }

    /**
     * Search events by name
     * GET /api/events/search/name?query=xyz
     */
    @GetMapping("/search/name")
    public ResponseEntity<List<EventDTO>> searchEventsByName(@RequestParam String query) {
        List<EventDTO> events = eventService.searchEventsByName(query);
        return ResponseEntity.ok(events);
    }

    /**
     * Search events by optional category within a required date range according to S2-F1
     * GET /api/events/search?category=CONCERT&startDate=2026-03-01&endDate=2026-03-31
     */
    @GetMapping("/search")
    public ResponseEntity<List<EventDTO>> searchEvents(
            @RequestParam(required = false) String category,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        List<EventDTO> events = eventService.searchEvents(category, startDate, endDate);
        return ResponseEntity.ok(events);
    }

    /**
     * Search events by venue
     * GET /api/events/search/venue?query=xyz
     */
    @GetMapping("/search/venue")
    public ResponseEntity<List<EventDTO>> searchEventsByVenue(@RequestParam String query) {
        List<EventDTO> events = eventService.searchEventsByVenue(query);
        return ResponseEntity.ok(events);
    }

    /**
     * Get upcoming events
     * GET /api/events/upcoming
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<EventDTO>> getUpcomingEvents() {
        List<EventDTO> events = eventService.getUpcomingEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Get events between dates
     * GET /api/events/between?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
     */
    @GetMapping("/between")
    public ResponseEntity<List<EventDTO>> getEventsBetweenDates(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        List<EventDTO> events = eventService.getEventsBetweenDates(startDate, endDate);
        return ResponseEntity.ok(events);
    }

    /**
     * Get events by minimum rating
     * GET /api/events/rating/{rating}
     */
    @GetMapping("/rating/{rating}")
    public ResponseEntity<List<EventDTO>> getEventsByMinimumRating(@PathVariable Double rating) {
        List<EventDTO> events = eventService.getEventsByMinimumRating(rating);
        return ResponseEntity.ok(events);
    }

    /**
     * Update an event
     * PUT /api/events/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(
            @PathVariable Long id,
            @RequestBody UpdateEventDTO request) {
        EventDTO event = eventService.updateEvent(id, request);
        return ResponseEntity.ok(event);
    }

    /**
     * Update event details according to S2-F2
     * PUT /api/events/{id}/details
     */
    @PutMapping("/{id}/details")
    public ResponseEntity<EventDTO> updateEventDetails(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        EventDTO event = eventService.updateEventDetails(id, request);
        return ResponseEntity.ok(event);
    }

    /**
     * Get event booking revenue summary according to S2-F3
     * GET /api/events/{id}/revenue?startDate=2026-03-01&endDate=2026-03-31
     */
    @GetMapping("/{id}/revenue")
    public ResponseEntity<EventRevenueDTO> getEventRevenueSummary(
            @PathVariable Long id,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        EventRevenueDTO summary = eventService.getEventRevenueSummary(id, startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get event performance dashboard according to S2-F12
     * GET /api/events/{id}/dashboard
     */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<EventDashboardDTO> getEventDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventDashboard(id));
    }

    /**
     * Update event status according to S2-F4
     * PUT /api/events/{id}/status
     * Body: {"status":"CANCELLED"}
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateEventStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String status = request.get("status");

        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required"
            );
        }

        eventService.updateEventStatus(id, status);
        return ResponseEntity.ok().build();
    }

    /**
     * Update event rating
     * PATCH /api/events/{id}/rating/{rating}
     */
    @PatchMapping("/{id}/rating/{rating}")
    public ResponseEntity<EventDTO> updateEventRating(
            @PathVariable Long id,
            @PathVariable Double rating) {
        EventDTO event = eventService.updateEventRating(id, rating);
        return ResponseEntity.ok(event);
    }

    /**
     * Delete an event
     * DELETE /api/events/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Filter events by details key-value pair and optional status
     * GET /api/events/details/search?key=organizer&value=LiveNation&status=UPCOMING
     */
    @GetMapping("/details/search")
    public ResponseEntity<List<EventDTO>> searchEventsByDetailAttribute(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) String status) {

        List<EventDTO> events = eventService.searchEventsByDetailAttribute(key, value, status);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/reports/top-rated")
    public ResponseEntity<List<TopEventDTO>> getTopRatedEvents(
            @RequestParam(defaultValue = "5") int limit) {

        return ResponseEntity.ok(eventService.getTopRatedEvents(limit));
    }

    /**
     * Rate an event after attendance according to S2-F7
     * POST /api/events/{id}/rate
     * Body: {"bookingId":1,"rating":5}
     */
    @PostMapping("/{id}/rate")
    public ResponseEntity<Void> rateEventAfterAttendance(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        Object bookingIdObj = request.get("bookingId");
        Object ratingObj = request.get("rating");

        if (bookingIdObj == null || ratingObj == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "bookingId and rating are required"
            );
        }

        Long bookingId = ((Number) bookingIdObj).longValue();
        Double rating = ((Number) ratingObj).doubleValue();

        if (rating % 1 != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating must be a whole number (1-5)"
            );
        }

        Integer finalRating = rating.intValue();
        eventService.rateEventAfterAttendance(id, bookingId, finalRating);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<Void> indexEvent(@PathVariable Long id) {
        eventIndexService.indexEvent(id, "explicit");
        return ResponseEntity.ok().build();
    }
}
