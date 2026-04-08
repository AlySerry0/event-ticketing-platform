package com.team7.eventticketing.event.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for updating an event session (partial update supported)
 */
public class UpdateEventSessionDTO {

    private String title;
    private String speaker;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Map<String, Object> metadata;

    public UpdateEventSessionDTO() {
    }

    public UpdateEventSessionDTO(String title,
                                 String speaker,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime,
                                 Integer capacity,
                                 Boolean verified,
                                 Map<String, Object> metadata) {
        this.title = title;
        this.speaker = speaker;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
        this.metadata = metadata;
    }

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