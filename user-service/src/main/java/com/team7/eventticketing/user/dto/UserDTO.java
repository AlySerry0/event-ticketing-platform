package com.team7.eventticketing.user.dto;

import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.model.UserStatus;
import com.team7.eventticketing.user.pattern.builder.BaseBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private UserRole role;
    private UserStatus status;
    private Map<String, Object> preferences;
    private LocalDateTime createdAt;
    private List<FavoriteVenueDTO> favoriteVenues;

    // Default constructor for Jackson/Frameworks
    public UserDTO() {}

    // Private constructor for the Builder
    private UserDTO(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.email = builder.email;
        this.phone = builder.phone;
        this.role = builder.role;
        this.status = builder.status;
        this.preferences = builder.preferences;
        this.createdAt = builder.createdAt;
        this.favoriteVenues = builder.favoriteVenues;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BaseBuilder<UserDTO, Builder> {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private UserRole role;
        private UserStatus status;
        private Map<String, Object> preferences;
        private LocalDateTime createdAt;
        private List<FavoriteVenueDTO> favoriteVenues;

        private Builder() {}

        public Builder id(Long id) {
            this.id = id;
            return self();
        }

        public Builder name(String name) {
            this.name = name;
            return self();
        }

        public Builder email(String email) {
            this.email = email;
            return self();
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return self();
        }

        public Builder role(UserRole role) {
            this.role = role;
            return self();
        }

        public Builder status(UserStatus status) {
            this.status = status;
            return self();
        }

        public Builder preferences(Map<String, Object> preferences) {
            this.preferences = preferences;
            return self();
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return self();
        }

        public Builder favoriteVenues(List<FavoriteVenueDTO> favoriteVenues) {
            this.favoriteVenues = favoriteVenues;
            return self();
        }

        @Override
        public UserDTO build() {
            return new UserDTO(this);
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<FavoriteVenueDTO> getFavoriteVenues() { return favoriteVenues; }
    public void setFavoriteVenues(List<FavoriteVenueDTO> favoriteVenues) { this.favoriteVenues = favoriteVenues; }
}