package com.team7.eventticketing.event.controller;

import com.team7.eventticketing.event.dto.CreateEventSessionDTO;
import com.team7.eventticketing.event.dto.EventSessionDTO;
import com.team7.eventticketing.event.dto.UpdateEventSessionDTO;
import com.team7.eventticketing.event.service.EventSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
    public ResponseEntity<List<EventSessionDTO>> getSessionsByEventId(@PathVariable Long eventId) {
        List<EventSessionDTO> sessions = eventSessionService.getSessionsByEventId(eventId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get verified sessions for an event
     * GET /api/events/{eventId}/sessions/verified
     */
    @GetMapping("/verified")
    public ResponseEntity<List<EventSessionDTO>> getVerifiedSessionsByEventId(@PathVariable Long eventId) {
        List<EventSessionDTO> sessions = eventSessionService.getVerifiedSessionsByEventId(eventId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Update an event session
     * PUT /api/events/{eventId}/sessions/{sessionId}
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<EventSessionDTO> updateEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody UpdateEventSessionDTO request) {
        EventSessionDTO session = eventSessionService.updateEventSession(eventId, sessionId, request);
        return ResponseEntity.ok(session);
    }

    /**
     * Verify an event session
     * PATCH /api/events/{eventId}/sessions/{sessionId}/verify
     */
    @PatchMapping("/{sessionId}/verify")
    public ResponseEntity<EventSessionDTO> verifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId) {
        EventSessionDTO session = eventSessionService.verifyEventSession(eventId, sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Unverify an event session
     * PATCH /api/events/{eventId}/sessions/{sessionId}/unverify
     */
    @PatchMapping("/{sessionId}/unverify")
    public ResponseEntity<EventSessionDTO> unverifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId) {
        EventSessionDTO session = eventSessionService.unverifyEventSession(eventId, sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Delete an event session
     * DELETE /api/events/{eventId}/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId) {
        eventSessionService.deleteEventSession(eventId, sessionId);
        return ResponseEntity.noContent().build();
    }

}