package com.team7.eventticketing.event.controller;

import com.team7.eventticketing.event.dto.EventSessionAlertDTO;
import com.team7.eventticketing.event.dto.EventSessionDTO;
import com.team7.eventticketing.event.service.EventSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/events/sessions")
public class EventSessionGlobalController {

    private final EventSessionService eventSessionService;

    public EventSessionGlobalController(EventSessionService eventSessionService) {
        this.eventSessionService = eventSessionService;
    }

    /**
     * Get event session by ID
     * GET /api/events/sessions/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<EventSessionDTO> getEventSessionById(
            @PathVariable Long sessionId) {
        EventSessionDTO session = eventSessionService.getEventSessionById(sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Get all verified sessions
     * GET /api/events/sessions/all-verified
     */
    @GetMapping("/all-verified")
    public ResponseEntity<List<EventSessionDTO>> getAllVerifiedSessions() {
        List<EventSessionDTO> sessions = eventSessionService.getAllVerifiedSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get all sessions
     * GET /api/events/sessions/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<EventSessionDTO>> getAllSessions() {
        List<EventSessionDTO> sessions = eventSessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Search sessions by title
     * GET /api/events/sessions/search/title?query=xyz
     */
    @GetMapping("/search/title")
    public ResponseEntity<List<EventSessionDTO>> searchSessionsByTitle(@RequestParam String query) {
        List<EventSessionDTO> sessions = eventSessionService.searchSessionsByTitle(query);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Search sessions by speaker
     * GET /api/events/sessions/search/speaker?query=xyz
     */
    @GetMapping("/search/speaker")
    public ResponseEntity<List<EventSessionDTO>> searchSessionsBySpeaker(@RequestParam String query) {
        List<EventSessionDTO> sessions = eventSessionService.searchSessionsBySpeaker(query);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get sessions between dates
     * GET /api/events/sessions/between?startTime=...&endTime=...
     */
    @GetMapping("/between")
    public ResponseEntity<List<EventSessionDTO>> getSessionsBetweenDates(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime) {
        List<EventSessionDTO> sessions = eventSessionService.getSessionsBetweenDates(startTime, endTime);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get sessions with available capacity
     * GET /api/events/sessions/available-capacity
     */
    @GetMapping("/available-capacity")
    public ResponseEntity<List<EventSessionDTO>> getSessionsWithAvailableCapacity() {
        List<EventSessionDTO> sessions = eventSessionService.getSessionsWithAvailableCapacity();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get events that have at least one unverified session
     * GET /api/events/sessions/unverified
     */
    @GetMapping("/unverified")
    public ResponseEntity<List<EventSessionAlertDTO>> getEventsWithUnverifiedSessions() {
        List<EventSessionAlertDTO> alerts = eventSessionService.getEventsWithUnverifiedSessions();
        return ResponseEntity.ok(alerts);
    }
}
