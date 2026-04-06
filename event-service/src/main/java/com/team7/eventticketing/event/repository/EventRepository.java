package com.team7.eventticketing.event.repository;

import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventCategory;
import com.team7.eventticketing.event.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Event entity
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Find all events by category
     */
    List<Event> findByCategory(EventCategory category);

    /**
     * Find all events by status
     */
    List<Event> findByStatus(EventStatus status);

    /**
     * Find events by category and status
     */
    List<Event> findByCategoryAndStatus(EventCategory category, EventStatus status);

    /**
     * Find events by name containing (case-insensitive)
     */
    List<Event> findByNameContainingIgnoreCase(String name);

    /**
     * Find events by venue containing (case-insensitive)
     */
    List<Event> findByVenueContainingIgnoreCase(String venue);

    /**
     * Find all upcoming events (by event date)
     */
    @Query("SELECT e FROM Event e WHERE e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingEvents(@Param("now") LocalDateTime now);

    /**
     * Find events between two dates
     */
    @Query("SELECT e FROM Event e WHERE e.eventDate BETWEEN :startDate AND :endDate ORDER BY e.eventDate ASC")
    List<Event> findEventsBetweenDates(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Find events by rating (greater than or equal to)
     */
    List<Event> findByRatingGreaterThanEqual(Double rating);


    /**
     * Find events by JSONB details key-value pair
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE e.details ->> :key = :value
            ORDER BY e.event_date ASC
            """, nativeQuery = true)
    List<Event> findByDetailAttribute(@Param("key") String key,
                                      @Param("value") String value);

    @Query(value = """
        SELECT * FROM events
        WHERE details ->> :key = :value
          AND status::text = :status
        ORDER BY event_date ASC
        """, nativeQuery = true)
    List<Event> findByDetailAttributeAndStatus(@Param("key") String key,
                                               @Param("value") String value,
                                               @Param("status") String status);
}