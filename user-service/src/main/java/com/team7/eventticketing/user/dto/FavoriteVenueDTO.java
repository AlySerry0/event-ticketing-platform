package com.team7.eventticketing.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class FavoriteVenueDTO {
    private Long id;
    private String label;
    private String venueName;
    private String location;
    private Integer capacity;
    private Boolean isDefault;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    // Constructors
    public FavoriteVenueDTO() {
    }

    public FavoriteVenueDTO(String label, String venueName, String location) {
        this.label = label;
        this.venueName = venueName;
        this.location = location;
        this.isDefault = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
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

