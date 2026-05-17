package com.team7.eventticketing.event.service;

import com.team7.eventticketing.event.dto.CreateEventSessionDTO;
import com.team7.eventticketing.event.dto.EventDTO;
import com.team7.eventticketing.event.dto.EventSessionAlertDTO;
import com.team7.eventticketing.event.dto.EventSessionDTO;
import com.team7.eventticketing.event.dto.UpdateEventSessionDTO;
import com.team7.eventticketing.event.dto.VerifyEventSessionDTO;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventSession;
import com.team7.eventticketing.event.observer.EntityObserver;
import com.team7.eventticketing.event.observer.MongoEventLogger;
import com.team7.eventticketing.event.repository.EventRepository;
import com.team7.eventticketing.event.repository.EventSessionRepository;
import com.team7.eventticketing.event.util.CacheInvalidationService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import com.team7.eventticketing.contracts.dto.UserDTO;
import com.team7.eventticketing.contracts.feign.UserServiceClient;
import feign.FeignException;

@Service
@Transactional(readOnly = true)
public class EventSessionService {
    private static final Logger log = LoggerFactory.getLogger(EventSessionService.class);
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final UserServiceClient userServiceClient;
    private final EventSessionRepository eventSessionRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;
    private final CacheInvalidationService cacheInvalidationService;

    public EventSessionService(EventSessionRepository eventSessionRepository,
                               EventRepository eventRepository,
                               EventService eventService,
                               MongoEventLogger mongoEventLogger,
                               CacheInvalidationService cacheInvalidationService,
                               UserServiceClient userServiceClient) {
        this.eventSessionRepository = eventSessionRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.register(mongoEventLogger);
        this.cacheInvalidationService = cacheInvalidationService;
        this.userServiceClient = userServiceClient;
    }

    public void register(EntityObserver observer) {
        if (!observers.contains(observer)) observers.add(observer);
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
    // Invalidation helper
    // Called after any write that touches an EventSession row.
    // Busts the session detail cache + S2-F9 (unverified sessions report).
    // -----------------------------------------------------------------------

    private void invalidateSessionCaches(Long sessionId) {
        // Entity detail cache — GET /api/event-sessions/{id}
        cacheInvalidationService.invalidateCacheWildcard(
                "event-service::event-session::" + sessionId + "::*");

        // S2-F9 unverified sessions report — session changes affect this list
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F9::*");
    }


    @Transactional
    public EventSessionDTO createEventSession(Long eventId, CreateEventSessionDTO sessionDTO) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId));

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

        // New session is unverified by default — S2-F9 list changes
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F9::*");

        return convertToDTO(savedSession);
    }

    // -----------------------------------------------------------------------
    // Read — CRUD get-by-ID cached (15 min via RedisConfig)
    // All list/search endpoints are NOT cached (list endpoints per §4.4.2)
    // -----------------------------------------------------------------------

    /**
     * GET /api/event-sessions/{id} — cached per §4.4.2
     */
    @Cacheable(value = "event-session", key = "#sessionId")
    public EventSessionDTO getEventSessionById(Long sessionId) {
        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId));
        return convertToDTO(session);
    }

    /**
     * NOT cached — involves two entities and is more of a lookup than a detail view
     */
    public EventSessionDTO getEventSessionByIdAndEventId(Long eventId, Long sessionId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found with id: " + eventId));

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId));

        if (!session.getEvent().getId().equals(event.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId);
        }

        return convertToDTO(session);
    }

    /**
     * NOT cached — list endpoint per §4.4.2
     */
    public List<EventSessionDTO> getSessionsByEventId(Long eventId) {
        ensureEventExists(eventId);
        return eventSessionRepository.findByEventId(eventId)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> getVerifiedSessionsByEventId(Long eventId) {
        ensureEventExists(eventId);
        return eventSessionRepository.findByEventIdAndVerified(eventId, true)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> getAllVerifiedSessions() {
        return eventSessionRepository.findByVerified(true)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> getAllSessions() {
        return eventSessionRepository.findAll()
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> searchSessionsByTitle(String title) {
        return eventSessionRepository.findByTitleContainingIgnoreCase(title)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> searchSessionsBySpeaker(String speaker) {
        return eventSessionRepository.findBySpeakerContainingIgnoreCase(speaker)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> getSessionsBetweenDates(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start time and end time are required");
        }
        if (startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start time must be before or equal to end time");
        }
        return eventSessionRepository.findSessionsBetweenDates(startTime, endTime)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * NOT cached — list endpoint
     */
    public List<EventSessionDTO> getSessionsWithAvailableCapacity() {
        return eventSessionRepository.findSessionsWithAvailableCapacity()
                .stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Writes — invalidate after every state change
    // -----------------------------------------------------------------------

    /**
     * PUT /api/events/{eventId}/sessions/{sessionId}
     * Write — invalidate session detail + S2-F9
     */
    @Transactional
    public EventSessionDTO updateEventSession(Long eventId, Long sessionId, UpdateEventSessionDTO request) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found with id: " + sessionId));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Session does not belong to event");
        }

        if (request.getCapacity() != null && request.getCapacity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Capacity must be greater than or equal to 0");
        }

        if (request.getStartTime() != null && request.getEndTime() != null) {
            if (request.getStartTime().isAfter(request.getEndTime())
                    || request.getStartTime().isEqual(request.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Start time must be before end time");
            }
        } else if (request.getStartTime() != null) {
            if (request.getStartTime().isAfter(session.getEndTime())
                    || request.getStartTime().isEqual(session.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Start time must be before end time");
            }
        } else if (request.getEndTime() != null) {
            if (session.getStartTime().isAfter(request.getEndTime())
                    || session.getStartTime().isEqual(request.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Start time must be before end time");
            }
        }

        if (request.getTitle() != null)     session.setTitle(request.getTitle());
        if (request.getSpeaker() != null)   session.setSpeaker(request.getSpeaker());
        if (request.getStartTime() != null) session.setStartTime(request.getStartTime());
        if (request.getEndTime() != null)   session.setEndTime(request.getEndTime());
        if (request.getCapacity() != null)  session.setCapacity(request.getCapacity());
        if (request.getMetadata() != null)  session.setMetadata(request.getMetadata());

        EventSessionDTO result = convertToDTO(eventSessionRepository.save(session));

        // Invalidate after save
        invalidateSessionCaches(sessionId);

        return result;
    }

    /**
     * PUT /api/events/{eventId}/sessions/{sessionId}/verify
     * S2-F8 is explicitly listed as a WRITE in §4.4.1 — invalidate
     */
    @Transactional
    public EventDTO verifyEventSession(Long eventId, Long sessionId, VerifyEventSessionDTO request) {
        try {
            MDC.put("eventId", eventId.toString());

            log.info("Received S2-F8 verify session request for eventId={} sessionId={}", eventId, sessionId);

            ensureEventExists(eventId);

            EventSession session = eventSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Event session not found with id: " + sessionId));

            if (!session.getEvent().getId().equals(eventId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Session does not belong to event with id: " + eventId);
            }

            if (request == null || request.getVerifiedBy() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "verifiedBy is required");
            }

            if (!session.getStartTime().isAfter(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot verify a session that already happened");
            }

            Long verifiedBy = request.getVerifiedBy();

            validateAdminUser(verifiedBy, "Verifier");

            session.setVerified(true);

            Map<String, Object> metadata = session.getMetadata() != null
                    ? new HashMap<>(session.getMetadata())
                    : new HashMap<>();

            metadata.put("verifiedAt", LocalDateTime.now().toString());
            metadata.put("verifiedBy", verifiedBy);
            session.setMetadata(metadata);

            eventSessionRepository.save(session);

            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", eventId);
            payload.put("sessionId", sessionId);
            payload.put("verifiedBy", verifiedBy);

            notifyObservers("SESSION_VERIFIED", payload);

            invalidateSessionCaches(sessionId);

            log.info("Processed S2-F8 session verification for eventId={} sessionId={} verifiedBy={}",
                    eventId, sessionId, verifiedBy);

            return eventService.convertToDTO(eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Event not found with id: " + eventId)));

        } catch (ResponseStatusException e) {
            log.warn("Failed S2-F8 session verification for eventId={} sessionId={}: {}",
                    eventId, sessionId, e.getReason());
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during S2-F8 session verification for eventId={} sessionId={}: {}",
                    eventId, sessionId, e.getMessage());
            throw e;

        } finally {
            MDC.remove("eventId");
        }
    }
    /**
     * Unverify — also a write, same invalidation rules as verify
     */
    @Transactional
    public EventSessionDTO unverifyEventSession(Long eventId, Long sessionId, Map<String, Object> request) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId);
        }

        Long unverifiedBy = null;
        if (request != null) {
            Object unverifiedByObj = request.get("unverifiedBy");
            if (unverifiedByObj instanceof Number) {
                unverifiedBy = ((Number) unverifiedByObj).longValue();
            }
        }

        if (unverifiedBy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unverifiedBy is required");
        }

        validateAdminUser(unverifiedBy, "Unverifier");

        session.setVerified(false);

        Map<String, Object> metadata = session.getMetadata() != null
                ? new HashMap<>(session.getMetadata())
                : new HashMap<>();
        metadata.remove("verifiedAt");
        metadata.remove("verifiedBy");
        session.setMetadata(metadata);

        EventSessionDTO result = convertToDTO(eventSessionRepository.save(session));

        // Unverifying puts the session back into the S2-F9 report
        invalidateSessionCaches(sessionId);

        return result;
    }

    /**
     * DELETE /api/events/{eventId}/sessions/{sessionId}
     * Write — invalidate session detail + S2-F9
     */
    @Transactional
    public void deleteEventSession(Long eventId, Long sessionId) {
        ensureEventExists(eventId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event session not found with id: " + sessionId));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Session does not belong to event with id: " + eventId);
        }

        eventSessionRepository.delete(session);

        // Invalidate after delete
        invalidateSessionCaches(sessionId);
    }

    // -----------------------------------------------------------------------
    // S2-F9 — Unverified sessions report
    // Cached in EventService not here — this method feeds it
    // -----------------------------------------------------------------------
    @Cacheable(value = "S2-F9", key = "'all'")
    public List<EventSessionAlertDTO> getEventsWithUnverifiedSessions() {
        return eventRepository.findEventsWithUnverifiedSessions()
                .stream()
                .map(event -> {
                    List<EventSessionDTO> unverifiedSessions = event.getEventSessions()
                            .stream()
                            .map(this::convertToDTO)
                            .collect(Collectors.toList());

                    return EventSessionAlertDTO.builder()
                            .eventId(event.getId())
                            .eventName(event.getName())
                            .eventStatus(event.getStatus().name())
                            .unverifiedSessions(unverifiedSessions)
                            .unverifiedCount(unverifiedSessions.size())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void ensureEventExists(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Event not found with id: " + eventId);
        }
    }

    private void validateSessionInput(LocalDateTime startTime, LocalDateTime endTime, Integer capacity) {
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start time and end time are required");
        }
        if (startTime.isAfter(endTime) || startTime.isEqual(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start time must be before end time");
        }
        if (capacity == null || capacity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Capacity must be greater than or equal to 0");
        }
    }

    private void validateAdminUser(Long userId, String actionName) {
        UserDTO user;

        try {
            log.info("Calling user-service.getUser for {} userId={}", actionName, userId);

            user = userServiceClient.getUser(userId);

            log.info("user-service.getUser returned successfully for {} userId={}", actionName, userId);

        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    actionName + " user not found"
            );

        } catch (FeignException e) {
            log.warn("user-service unavailable while validating {} user {}: {}",
                    actionName, userId, e.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable"
            );
        }

        if (user.role() == null || !"ADMIN".equalsIgnoreCase(user.role().name())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    actionName + " must be an admin user"
            );
        }
    }

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