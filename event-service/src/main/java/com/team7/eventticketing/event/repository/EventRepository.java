package com.team7.eventticketing.event.repository;

import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.model.EventCategory;
import com.team7.eventticketing.event.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    List<Event> findByEventDateBetweenOrderByEventDateAsc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Event> findByCategoryAndEventDateBetweenOrderByEventDateAsc(
            EventCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Event> findAllByOrderByEventDateAsc();


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

    /**
     * Count active bookings for an event.
     * Active bookings are PENDING, CONFIRMED, CHECKED_IN.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM bookings b
            WHERE b.event_id = :eventId
              AND b.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
            """, nativeQuery = true)
    long countActiveBookingsForEvent(@Param("eventId") Long eventId);

    @Query(value = """
    SELECT e.id, e.name, e.rating, 0 AS total_bookings
    FROM events e
    WHERE e.rating IS NOT NULL
    ORDER BY e.rating DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findTopRatedEvents(@Param("limit") int limit);

    /**
     * Aggregate completed booking revenue for an event within a date range.
     */
    @Query(value = """
            SELECT COUNT(*) AS total_bookings,
                   COALESCE(SUM(b.total_amount), 0),
                   COALESCE(AVG(b.total_amount), 0)
            FROM bookings b
            WHERE b.event_id = :eventId
              AND b.status = 'COMPLETED'
              AND b.booking_date BETWEEN :startDate AND :endDate
            """, nativeQuery = true)
    Object[] findEventRevenueSummary(@Param("eventId") Long eventId,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT
                (SELECT COUNT(*)
                 FROM bookings b
                 WHERE b.event_id = :eventId) AS total_bookings,
                (SELECT COALESCE(SUM(b.total_amount), 0)
                 FROM bookings b
                 WHERE b.event_id = :eventId
                   AND b.status = 'COMPLETED') AS total_revenue,
                (SELECT COUNT(*)
                 FROM tickets t
                 JOIN bookings b ON b.id = t.booking_id
                 WHERE b.event_id = :eventId) AS total_tickets_sold,
                (SELECT COUNT(*)
                 FROM tickets t
                 JOIN bookings b ON b.id = t.booking_id
                 WHERE b.event_id = :eventId
                   AND t.status = 'USED') AS used_tickets
            """, nativeQuery = true)
    Object[] findEventDashboardMetrics(@Param("eventId") Long eventId);

    /**
     * Check whether a booking exists by ID
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM bookings b
            WHERE b.id = :bookingId
            """, nativeQuery = true)
    long countBookingById(@Param("bookingId") Long bookingId);

    /**
     * Check whether the booking belongs to the event and is COMPLETED
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM bookings b
            WHERE b.id = :bookingId
              AND b.event_id = :eventId
              AND b.status = 'COMPLETED'
            """, nativeQuery = true)
    long countCompletedBookingForEvent(@Param("bookingId") Long bookingId,
                                       @Param("eventId") Long eventId);

    /**
     * Find all events that have at least one unverified session
     */
    @Query("""
       SELECT DISTINCT e
       FROM Event e
       JOIN FETCH e.eventSessions s
       WHERE s.verified = false
       """)
    List<Event> findEventsWithUnverifiedSessions();

    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean userExistsByEmail(String email);
}
