package com.team7.eventticketing.user.dto;

import com.team7.eventticketing.user.pattern.builder.BaseBuilder;
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

    // Default constructor for Jackson/Frameworks
    public FavoriteVenueDTO() {}

    // Private constructor for the Builder
    private FavoriteVenueDTO(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.venueName = builder.venueName;
        this.location = builder.location;
        this.capacity = builder.capacity;
        this.isDefault = builder.isDefault;
        this.metadata = builder.metadata;
        this.createdAt = builder.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BaseBuilder<FavoriteVenueDTO, Builder> {
        private Long id;
        private String label;
        private String venueName;
        private String location;
        private Integer capacity;
        private Boolean isDefault;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;

        private Builder() {}

        public Builder id(Long id) {
            this.id = id;
            return self();
        }

        public Builder label(String label) {
            this.label = label;
            return self();
        }

        public Builder venueName(String venueName) {
            this.venueName = venueName;
            return self();
        }

        public Builder location(String location) {
            this.location = location;
            return self();
        }

        public Builder capacity(Integer capacity) {
            this.capacity = capacity;
            return self();
        }

        public Builder isDefault(Boolean isDefault) {
            this.isDefault = isDefault;
            return self();
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return self();
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return self();
        }

        @Override
        public FavoriteVenueDTO build() {
            return new FavoriteVenueDTO(this);
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}