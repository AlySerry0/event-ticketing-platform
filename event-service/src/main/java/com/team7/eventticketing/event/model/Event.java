package com.team7.eventticketing.event.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Event Entity - Represents a ticketed event
 * Table: events
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(nullable = false)
    private Integer totalRatings = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventSession> eventSessions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructors
    public Event() {
    }

    public Event(String name, String venue, LocalDateTime eventDate, EventCategory category, EventStatus status) {
        this.name = name;
        this.venue = venue;
        this.eventDate = eventDate;
        this.category = category;
        this.status = status;
        this.rating = 0.0;
        this.totalRatings = 0;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public EventCategory getCategory() {
        return category;
    }

    public void setCategory(EventCategory category) {
        this.category = category;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<EventSession> getEventSessions() {
        return eventSessions;
    }

    public void setEventSessions(List<EventSession> eventSessions) {
        this.eventSessions = eventSessions;
    }

    public void addEventSession(EventSession eventSession) {
        if (!eventSessions.contains(eventSession)) {
            eventSessions.add(eventSession);
            eventSession.setEvent(this);
        }
    }

    public void removeEventSession(EventSession eventSession) {
        if (eventSessions.contains(eventSession)) {
            eventSessions.remove(eventSession);
            eventSession.setEvent(null);
        }
    }

    @Override
    public String toString() {
        return "Event{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", venue='" + venue + '\'' +
                ", eventDate=" + eventDate +
                ", category=" + category +
                ", status=" + status +
                ", rating=" + rating +
                ", totalRatings=" + totalRatings +
                ", createdAt=" + createdAt +
                '}';
    }
}

