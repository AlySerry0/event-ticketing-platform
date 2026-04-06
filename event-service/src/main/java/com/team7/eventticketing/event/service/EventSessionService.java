package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.dto.CreateEventSessionDTO;
import com.team7.eventticketing.event.dto.EventSessionAlertDTO;
import com.team7.eventticketing.event.dto.EventSessionDTO;
import com.team7.eventticketing.event.dto.UpdateEventSessionDTO;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventSession;
import com.team7.eventticketing.event.repository.EventRepository;
import com.team7.eventticketing.event.repository.EventSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for EventSession operations
 */
@Service
@Transactional(readOnly = true)
public class EventSessionService {

    private final EventSessionRepository eventSessionRepository;
    private final EventRepository eventRepository;

    public EventSessionService(EventSessionRepository eventSessionRepository,
                               EventRepository eventRepository) {
        this.eventSessionRepository = eventSessionRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Create a new event session
     */
    @Transactional
    public EventSessionDTO createEventSession(Long eventId, CreateEventSessionDTO sessionDTO) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));

        validateSessionInput(sessionDTO.getStartTime(), sessionDTO.getEndTime(), sessionDTO.getCapacity());

        EventSession session = new EventSession(
                sessionDTO.getTitle(),
                sessionDTO.getStartTime(),
                sessionDTO.getEndTime(),
                sessionDTO.getCapacity()
        );

        session.setSpeaker(sessionDTO.getSpeaker());
        session.setMetadata(sessionDTO.getMetadata());
        session.setEvent(event);
        event.getEventSessions().add(session);

        EventSession savedSession = eventSessionRepository.save(session);
        return convertToDTO(savedSession);
    }

    /**
     * Get event session by ID
     */
    public EventSessionDTO getEventSessionById(Long sessionId) {
        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId
                ));
        return convertToDTO(session);
    }

    /**
     * Get event session by ID and event ID
     */
    public EventSessionDTO getEventSessionByIdAndEventId(Long eventId, Long sessionId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId
                ));

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId
                ));

        if (!session.getEvent().getId().equals(event.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId
            );
        }

        return convertToDTO(session);
    }



    /**
     * Get all sessions for a specific event
     */
    public List<EventSessionDTO> getSessionsByEventId(Long eventId) {
        ensureEventExists(eventId);

        return eventSessionRepository.findByEventId(eventId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all verified sessions for an event
     */
    public List<EventSessionDTO> getVerifiedSessionsByEventId(Long eventId) {
        ensureEventExists(eventId);

        return eventSessionRepository.findByEventIdAndVerified(eventId, true)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all verified sessions
     */
    public List<EventSessionDTO> getAllVerifiedSessions() {
        return eventSessionRepository.findByVerified(true)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all sessions
     */
    public List<EventSessionDTO> getAllSessions() {
        return eventSessionRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search sessions by title
     */
    public List<EventSessionDTO> searchSessionsByTitle(String title) {
        return eventSessionRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search sessions by speaker
     */
    public List<EventSessionDTO> searchSessionsBySpeaker(String speaker) {
        return eventSessionRepository.findBySpeakerContainingIgnoreCase(speaker)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get sessions between dates
     */
    public List<EventSessionDTO> getSessionsBetweenDates(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time and end time are required"
            );
        }

        if (startTime.isAfter(endTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time must be before or equal to end time"
            );
        }

        return eventSessionRepository.findSessionsBetweenDates(startTime, endTime)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get sessions with available capacity
     */
    public List<EventSessionDTO> getSessionsWithAvailableCapacity() {
        return eventSessionRepository.findSessionsWithAvailableCapacity()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update an event session
     */
    @Transactional
    public EventSessionDTO updateEventSession(Long eventId, Long sessionId, UpdateEventSessionDTO request) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found with id: " + sessionId
                ));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session does not belong to event"
            );
        }

        if(request.getCapacity()!=null && request.getCapacity()<0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Capacity must be greater than or equal to 0"
            );
        }

        if(request.getStartTime() != null && request.getEndTime() != null) {
            if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Start time must be before end time"
                );
            }
        }
        else if(request.getStartTime() != null) {
            if (request.getStartTime().isAfter(session.getEndTime()) || request.getStartTime().isEqual(session.getEndTime())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Start time must be before end time"
                );
            }
        }
        else if(request.getEndTime() != null) {
            if (session.getStartTime().isAfter(request.getEndTime()) || session.getStartTime().isEqual(request.getEndTime())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Start time must be before end time"
                );
            }
        }

        if (request.getTitle() != null) {
            session.setTitle(request.getTitle());
        }

        if (request.getSpeaker() != null) {
            session.setSpeaker(request.getSpeaker());
        }

        if (request.getStartTime() != null) {
            session.setStartTime(request.getStartTime());
        }

        if (request.getEndTime() != null) {
            session.setEndTime(request.getEndTime());
        }

        if (request.getCapacity() != null) {
            session.setCapacity(request.getCapacity());
        }

        if (request.getVerified() != null) {
            session.setVerified(request.getVerified());
        }

        if (request.getMetadata() != null) {
            session.setMetadata(request.getMetadata());
        }

        return convertToDTO(eventSessionRepository.save(session));
    }

    /**
     * Verify an event session
     */
    @Transactional
    public EventSessionDTO verifyEventSession(Long eventId, Long sessionId) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId
                ));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId
            );
        }

        session.setVerified(true);
        EventSession updatedSession = eventSessionRepository.save(session);
        return convertToDTO(updatedSession);
    }

    /**
     * Unverify an event session
     */
    @Transactional
    public EventSessionDTO unverifyEventSession(Long eventId, Long sessionId) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId
                ));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId
            );
        }

        session.setVerified(false);
        EventSession updatedSession = eventSessionRepository.save(session);
        return convertToDTO(updatedSession);
    }

    /**
     * Delete an event session
     */
    @Transactional
    public void deleteEventSession(Long eventId, Long sessionId) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId
                ));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId
            );
        }

        eventSessionRepository.delete(session);
    }

    public List<EventSessionAlertDTO> getEventsWithUnverifiedSessions() {
        return eventRepository.findEventsWithUnverifiedSessions()
                .stream()
                .map(event -> {
                    List<EventSessionDTO> unverifiedSessions = event.getEventSessions()
                            .stream()
                            .filter(session -> Boolean.FALSE.equals(session.getVerified()))
                            .map(this::convertToDTO)
                            .collect(Collectors.toList());

                    return new EventSessionAlertDTO(
                            event.getId(),
                            event.getName(),
                            event.getStatus().name(),
                            unverifiedSessions,
                            unverifiedSessions.size()
                    );
                })
                .filter(alert -> alert.getUnverifiedCount() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Ensure event exists
     */
    private void ensureEventExists(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Event not found with id: " + eventId
            );
        }
    }

    /**
     * Validate session input
     */
    private void validateSessionInput(LocalDateTime startTime, LocalDateTime endTime, Integer capacity) {
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time and end time are required"
            );
        }

        if (startTime.isAfter(endTime) || startTime.isEqual(endTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time must be before end time"
            );
        }

        if (capacity == null || capacity < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Capacity must be greater than or equal to 0"
            );
        }
    }

    /**
     * Convert EventSession entity to EventSessionDTO
     */
    private EventSessionDTO convertToDTO(EventSession session) {
        return new EventSessionDTO(
                session.getId(),
                session.getTitle(),
                session.getSpeaker(),
                session.getStartTime(),
                session.getEndTime(),
                session.getCapacity(),
                session.getVerified(),
                session.getMetadata(),
                session.getCreatedAt()
        );
    }
}