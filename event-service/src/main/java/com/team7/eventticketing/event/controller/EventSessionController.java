package com.team7.eventticketing.event.controller;

import com.team7.eventticketing.event.dto.*;
import com.team7.eventticketing.event.service.EventSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for EventSession operations
 */
@RestController
@RequestMapping("/api/events/{eventId}/sessions")
public class EventSessionController {

    private final EventSessionService eventSessionService;

    public EventSessionController(EventSessionService eventSessionService) {
        this.eventSessionService = eventSessionService;
    }

    /**
     * Create a new event session
     * POST /api/events/{eventId}/sessions
     */
    @PostMapping
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<EventSessionDTO> createEventSession(
            @PathVariable Long eventId,
            @RequestBody CreateEventSessionDTO request) {
        EventSessionDTO session = eventSessionService.createEventSession(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * Get event session by ID
     * GET /api/events/{eventId}/sessions/{sessionId}
     */
    @GetMapping("/{sessionId}")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<EventSessionDTO> getEventSessionById(
            @PathVariable Long eventId,
            @PathVariable Long sessionId) {
        EventSessionDTO session = eventSessionService.getEventSessionByIdAndEventId(eventId, sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Get all sessions for an event
     * GET /api/events/{eventId}/sessions
     */
    @GetMapping
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<List<EventSessionDTO>> getSessionsByEventId(@PathVariable Long eventId) {
        List<EventSessionDTO> sessions = eventSessionService.getSessionsByEventId(eventId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get verified sessions for an event
     * GET /api/events/{eventId}/sessions/verified
     */
    @GetMapping("/verified")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<List<EventSessionDTO>> getVerifiedSessionsByEventId(@PathVariable Long eventId) {
        List<EventSessionDTO> sessions = eventSessionService.getVerifiedSessionsByEventId(eventId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Update an event session
     * PUT /api/events/{eventId}/sessions/{sessionId}
     */
    @PutMapping("/{sessionId}")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<EventSessionDTO> updateEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody UpdateEventSessionDTO request) {
        EventSessionDTO session = eventSessionService.updateEventSession(eventId, sessionId, request);
        return ResponseEntity.ok(session);
    }

    /**
     * Verify an event session
     * PUT /api/events/{eventId}/sessions/{sessionId}/verify
     */
    @PutMapping("/{sessionId}/verify")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<EventDTO> verifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody VerifyEventSessionDTO request) {
        EventDTO event = eventSessionService.verifyEventSession(eventId, sessionId, request);
        return ResponseEntity.ok(event);
    }

    /**
     * Unverify an event session
     * PUT /api/events/{eventId}/sessions/{sessionId}/unverify
     */
    @PutMapping("/{sessionId}/unverify")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<EventSessionDTO> unverifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        EventSessionDTO session = eventSessionService.unverifyEventSession(eventId, sessionId, request);
        return ResponseEntity.ok(session);
    }

    /**
     * Delete an event session
     * DELETE /api/events/{eventId}/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId) {
        eventSessionService.deleteEventSession(eventId, sessionId);
        return ResponseEntity.noContent().build();
    }

}