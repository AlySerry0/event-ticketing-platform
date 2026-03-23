package com.team7.eventticketing.event.repository;

import com.team7.eventticketing.event.model.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for EventSession entity
 */
@Repository
public interface EventSessionRepository extends JpaRepository<EventSession, Long> {

    /**
     * Find all sessions for a specific event
     */
    List<EventSession> findByEventId(Long eventId);

    /**
     * Find sessions by event id and verified status
     */
    List<EventSession> findByEventIdAndVerified(Long eventId, Boolean verified);

    /**
     * Find all verified sessions
     */
    List<EventSession> findByVerified(Boolean verified);

    /**
     * Find sessions by title containing (case-insensitive)
     */
    List<EventSession> findByTitleContainingIgnoreCase(String title);

    /**
     * Find sessions by speaker containing (case-insensitive)
     */
    List<EventSession> findBySpeakerContainingIgnoreCase(String speaker);

    /**
     * Find sessions between two dates
     */
    @Query("SELECT es FROM EventSession es WHERE es.startTime >= :startTime AND es.endTime <= :endTime ORDER BY es.startTime ASC")
    List<EventSession> findSessionsBetweenDates(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * Find sessions with available capacity
     */
    @Query("SELECT es FROM EventSession es WHERE es.capacity > 0 ORDER BY es.startTime ASC")
    List<EventSession> findSessionsWithAvailableCapacity();
}