package com.team7.eventticketing.user.dto;

import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.model.UserStatus;

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

    // Constructors
    public UserDTO() {
    }

    public UserDTO(Long id, String name, String email, String phone, UserRole role, UserStatus status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
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

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<FavoriteVenueDTO> getFavoriteVenues() {
        return favoriteVenues;
    }

    public void setFavoriteVenues(List<FavoriteVenueDTO> favoriteVenues) {
        this.favoriteVenues = favoriteVenues;
    }
}

