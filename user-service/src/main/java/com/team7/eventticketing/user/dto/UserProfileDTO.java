package com.team7.eventticketing.user.dto;

import com.team7.eventticketing.user.pattern.builder.BaseBuilder;

import java.util.List;
import java.util.Map;

public class UserProfileDTO {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<FavoriteVenueDTO> favoriteVenues;
    private int totalFavoriteVenues;

    public UserProfileDTO() {}

    private UserProfileDTO(Builder builder) {
        this.userId              = builder.userId;
        this.name                = builder.name;
        this.email               = builder.email;
        this.phone               = builder.phone;
        this.preferences         = builder.preferences;
        this.favoriteVenues      = builder.favoriteVenues;
        this.totalFavoriteVenues = builder.totalFavoriteVenues;
    }

    // Legacy all-args constructor kept for backward compatibility
    public UserProfileDTO(Long userId, String name, String email, String phone,
                          Map<String, Object> preferences,
                          List<FavoriteVenueDTO> favoriteVenues,
                          int totalFavoriteVenues) {
        this.userId              = userId;
        this.name                = name;
        this.email               = email;
        this.phone               = phone;
        this.preferences         = preferences;
        this.favoriteVenues      = favoriteVenues;
        this.totalFavoriteVenues = totalFavoriteVenues;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BaseBuilder<UserProfileDTO, Builder> {

        private Long userId;
        private String name;
        private String email;
        private String phone;
        private Map<String, Object> preferences;
        private List<FavoriteVenueDTO> favoriteVenues;
        private int totalFavoriteVenues;

        private Builder() {}

        public Builder userId(Long userId) {
            this.userId = userId;
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

        public Builder preferences(Map<String, Object> preferences) {
            this.preferences = preferences;
            return self();
        }

        public Builder favoriteVenues(List<FavoriteVenueDTO> favoriteVenues) {
            this.favoriteVenues = favoriteVenues;
            return self();
        }

        public Builder totalFavoriteVenues(int totalFavoriteVenues) {
            this.totalFavoriteVenues = totalFavoriteVenues;
            return self();
        }

        @Override
        public UserProfileDTO build() {
            return new UserProfileDTO(this);
        }
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }
    public List<FavoriteVenueDTO> getFavoriteVenues() { return favoriteVenues; }
    public void setFavoriteVenues(List<FavoriteVenueDTO> favoriteVenues) { this.favoriteVenues = favoriteVenues; }
    public int getTotalFavoriteVenues() { return totalFavoriteVenues; }
    public void setTotalFavoriteVenues(int totalFavoriteVenues) { this.totalFavoriteVenues = totalFavoriteVenues; }
}