package com.team7.eventticketing.event.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Event - used for requests and responses
 */
public class EventDTO {

    private Long id;
    private String name;
    private String venue;
    private LocalDateTime eventDate;
    private String category;
    private String status;
    private Double rating;
    private Integer totalRatings;
    private Map<String, Object> details;
    private LocalDateTime createdAt;
    private List<EventSessionDTO> eventSessions;

    // Constructors
    public EventDTO() {
    }

    public EventDTO(Long id, String name, String venue, LocalDateTime eventDate,
                   String category, String status, Double rating, Integer totalRatings,
                   Map<String, Object> details, LocalDateTime createdAt, List<EventSessionDTO> eventSessions) {
        this.id = id;
        this.name = name;
        this.venue = venue;
        this.eventDate = eventDate;
        this.category = category;
        this.status = status;
        this.rating = rating;
        this.totalRatings = totalRatings;
        this.details = details;
        this.createdAt = createdAt;
        this.eventSessions = eventSessions;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public List<EventSessionDTO> getEventSessions() {
        return eventSessions;
    }

    public void setEventSessions(List<EventSessionDTO> eventSessions) {
        this.eventSessions = eventSessions;
    }
}

