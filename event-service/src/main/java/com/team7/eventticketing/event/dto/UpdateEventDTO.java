package com.team7.eventticketing.event.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for updating an existing event (partial update supported)
 */
public class UpdateEventDTO {

    private String name;
    private String venue;
    private LocalDateTime eventDate;
    private String category;
    private String status;
    private Map<String,Object> details;

    public UpdateEventDTO() {
    }

    public UpdateEventDTO(String name,
                          String venue,
                          LocalDateTime eventDate,
                          String category,
                          String status,
                          Map<String,Object> details) {
        this.name = name;
        this.venue = venue;
        this.eventDate = eventDate;
        this.category = category;
        this.status = status;
        this.details = details;
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

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String,Object> details) {
        this.details = details;
    }
}