package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.FavoriteVenueDTO;
import com.team7.eventticketing.user.dto.UserDTO;
import com.team7.eventticketing.user.model.FavoriteVenue;
import com.team7.eventticketing.user.model.User;
import com.team7.eventticketing.user.repository.FavoriteVenueRepository;
import com.team7.eventticketing.user.repository.UserRepository;
import com.team7.eventticketing.user.util.CacheInvalidationService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FavoriteVenueService {

    private final FavoriteVenueRepository favoriteVenueRepository;
    private final UserRepository userRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public FavoriteVenueService(FavoriteVenueRepository favoriteVenueRepository,
                                UserRepository userRepository,
                                CacheInvalidationService cacheInvalidationService) {
        this.favoriteVenueRepository = favoriteVenueRepository;
        this.userRepository = userRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    /**
     * Add a favorite venue.
     * Per PDF §4.4.4 POST rule: nothing to invalidate.
     */
    @Transactional
    public FavoriteVenueDTO addFavoriteVenue(Long userId, FavoriteVenueDTO favoriteVenueDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + userId
                ));

        FavoriteVenue favoriteVenue = new FavoriteVenue(
                favoriteVenueDTO.getLabel(),
                favoriteVenueDTO.getVenueName(),
                favoriteVenueDTO.getLocation(),
                user
        );

        if (favoriteVenueDTO.getCapacity() != null) {
            favoriteVenue.setCapacity(favoriteVenueDTO.getCapacity());
        }
        if (favoriteVenueDTO.getIsDefault() != null) {
            favoriteVenue.setIsDefault(favoriteVenueDTO.getIsDefault());
        }
        if (favoriteVenueDTO.getMetadata() != null) {
            favoriteVenue.setMetadata(favoriteVenueDTO.getMetadata());
        }

        FavoriteVenue savedVenue = favoriteVenueRepository.save(favoriteVenue);
        user.getFavoriteVenues().add(savedVenue);
        return convertToDTO(savedVenue);
    }

    /**
     * Get all favorite venues for a user (NOT cached — list endpoint per PDF §4.4.2)
     */
    public List<FavoriteVenueDTO> getUserFavoriteVenues(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found with ID: " + userId
            );
        }

        return favoriteVenueRepository.findByUserId(userId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get favorite venue by ID — CRUD detail cache (15 min)
     */
    @Cacheable(value = "favorite-venue", key = "#id")
    public FavoriteVenueDTO getFavoriteVenueById(Long id) {
        FavoriteVenue favoriteVenue = favoriteVenueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Favorite venue not found with ID: " + id
                ));
        return convertToDTO(favoriteVenue);
    }

    /**
     * Update favorite venue — invalidate detail + S1-F8 (profile uses venues)
     */
    @Transactional
    public FavoriteVenueDTO updateFavoriteVenue(Long id, FavoriteVenueDTO favoriteVenueDTO) {
        FavoriteVenue favoriteVenue = favoriteVenueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Favorite venue not found with ID: " + id
                ));

        if (favoriteVenueDTO.getLabel() != null) {
            favoriteVenue.setLabel(favoriteVenueDTO.getLabel());
        }
        if (favoriteVenueDTO.getVenueName() != null) {
            favoriteVenue.setVenueName(favoriteVenueDTO.getVenueName());
        }
        if (favoriteVenueDTO.getLocation() != null) {
            favoriteVenue.setLocation(favoriteVenueDTO.getLocation());
        }
        if (favoriteVenueDTO.getCapacity() != null) {
            favoriteVenue.setCapacity(favoriteVenueDTO.getCapacity());
        }
        if (favoriteVenueDTO.getIsDefault() != null) {
            favoriteVenue.setIsDefault(favoriteVenueDTO.getIsDefault());
        }
        if (favoriteVenueDTO.getMetadata() != null) {
            favoriteVenue.setMetadata(favoriteVenueDTO.getMetadata());
        }

        FavoriteVenue updatedVenue = favoriteVenueRepository.save(favoriteVenue);
        invalidateVenueCaches(id);
        return convertToDTO(updatedVenue);
    }

    /**
     * Delete favorite venue — invalidate detail + S1-F8
     */
    @Transactional
    public void deleteFavoriteVenue(Long id) {
        if (!favoriteVenueRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Favorite venue not found with ID: " + id
            );
        }
        favoriteVenueRepository.deleteById(id);
        invalidateVenueCaches(id);
    }

    /**
     * Get default favorite venue for a user (NOT cached — not in §4.4 enumeration)
     */
    public FavoriteVenueDTO getDefaultFavoriteVenue(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found with ID: " + userId
            );
        }

        FavoriteVenue favoriteVenue = favoriteVenueRepository.findByUserIdAndIsDefaultTrue(userId);
        if (favoriteVenue == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No default favorite venue found for user ID: " + userId
            );
        }

        return convertToDTO(favoriteVenue);
    }

    /**
     * [S1-F7] Set Default Favorite Venue — invalidate ALL favorite-venue keys + S1-F8
     * (multiple venues' isDefault flag flips, so wildcard for safety)
     */
    @Transactional
    public UserDTO setDefaultFavoriteVenue(Long userId, Long venueId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found with ID: " + userId));

        FavoriteVenue venue = favoriteVenueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Venue not found with ID: " + venueId));

        if (!venue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Venue does not belong to this user");
        }

        favoriteVenueRepository.resetAllDefaultsForUser(userId);

        venue.setIsDefault(true);
        favoriteVenueRepository.save(venue);

        User updatedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found with ID: " + userId));

        // Multiple venue rows changed → wildcard their detail cache + invalidate profile
        cacheInvalidationService.invalidateCacheWildcard("user-service::favorite-venue::*");
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F8::*");

        return convertToDTO(updatedUser);
    }

    private void invalidateVenueCaches(Long venueId) {
        cacheInvalidationService.invalidateCacheWildcard("user-service::favorite-venue::" + venueId);
        // S1-F8 profile DTO embeds favorite venues — invalidate so updates surface
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F8::*");
    }

    private UserDTO convertToDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setName(user.getName());
        userDTO.setEmail(user.getEmail());
        userDTO.setPhone(user.getPhone());
        userDTO.setRole(user.getRole());
        userDTO.setStatus(user.getStatus());
        userDTO.setPreferences(user.getPreferences());
        userDTO.setCreatedAt(user.getCreatedAt());
        return userDTO;
    }

    private FavoriteVenueDTO convertToDTO(FavoriteVenue favoriteVenue) {
        FavoriteVenueDTO dto = new FavoriteVenueDTO();
        dto.setId(favoriteVenue.getId());
        dto.setLabel(favoriteVenue.getLabel());
        dto.setVenueName(favoriteVenue.getVenueName());
        dto.setLocation(favoriteVenue.getLocation());
        dto.setCapacity(favoriteVenue.getCapacity());
        dto.setIsDefault(favoriteVenue.getIsDefault());
        dto.setMetadata(favoriteVenue.getMetadata());
        dto.setCreatedAt(favoriteVenue.getCreatedAt());
        return dto;
    }
}