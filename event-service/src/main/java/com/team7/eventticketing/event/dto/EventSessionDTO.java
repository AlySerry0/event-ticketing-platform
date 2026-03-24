package com.team7.eventticketing.event.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for EventSession - used for requests and responses
 */
public class EventSessionDTO {

    private Long id;
    private String title;
    private String speaker;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Boolean verified;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    // Constructors
    public EventSessionDTO() {
    }

    public EventSessionDTO(Long id, String title, String speaker, LocalDateTime startTime,
                           LocalDateTime endTime, Integer capacity, Boolean verified,
                           Map<String, Object> metadata, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.speaker = speaker;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
        this.verified = verified;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
