package com.team7.eventticketing.user.dto;

import java.util.List;
import java.util.Map;

public class UserProfileDTO {
    //userId, name, email, phone, preferences (JSONB), favoriteVenues
    //(list of venue objects with label, venueName, location, capacity, isDefault, metadata), totalFavoriteVenues
    //(count)

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String,Object> preferences;
    private List<FavoriteVenueDTO> favoriteVenues;
    private int totalFavoriteVenues;

    public UserProfileDTO() {
    }

    public UserProfileDTO(Long userId, String name, String email, String phone, Map<String,Object> preferences, List<FavoriteVenueDTO> favoriteVenues, int totalFavoriteVenues) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.preferences = preferences;
        this.favoriteVenues = favoriteVenues;
        this.totalFavoriteVenues = totalFavoriteVenues;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Map<String,Object> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String,Object> preferences) {
        this.preferences = preferences;
    }

    public List<FavoriteVenueDTO> getFavoriteVenues() {
        return favoriteVenues;
    }

    public void setFavoriteVenues(List<FavoriteVenueDTO> favoriteVenues) {
        this.favoriteVenues = favoriteVenues;
    }

    public int getTotalFavoriteVenues() {
        return totalFavoriteVenues;
    }

    public void setTotalFavoriteVenues(int totalFavoriteVenues) {
        this.totalFavoriteVenues = totalFavoriteVenues;
    }
}
