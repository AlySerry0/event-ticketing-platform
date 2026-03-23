package com.team7.eventticketing.event.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for creating/updating EventSession
 */
public class CreateEventSessionDTO {

    private String title;
    private String speaker;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Map<String, Object> metadata;

    // Constructors
    public CreateEventSessionDTO() {
    }

    public CreateEventSessionDTO(String title, LocalDateTime startTime,
                                 LocalDateTime endTime, Integer capacity) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}

