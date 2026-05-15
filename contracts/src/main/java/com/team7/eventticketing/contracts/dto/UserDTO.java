package com.team7.eventticketing.contracts.dto;

import com.team7.eventticketing.contracts.enums.UserRole;
import com.team7.eventticketing.contracts.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record UserDTO(Long id, String name,
                      String email, String phone, UserRole role,
                      UserStatus status, Map<String, Object> preferences,
                      LocalDateTime createdAt, List<FavoriteVenueDTO> favoriteVenues) {
}