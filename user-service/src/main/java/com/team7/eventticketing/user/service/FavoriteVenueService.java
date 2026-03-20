package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.FavoriteVenueDTO;
import com.team7.eventticketing.user.model.FavoriteVenue;
import com.team7.eventticketing.user.model.User;
import com.team7.eventticketing.user.repository.FavoriteVenueRepository;
import com.team7.eventticketing.user.repository.UserRepository;
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

    public FavoriteVenueService(FavoriteVenueRepository favoriteVenueRepository, UserRepository userRepository) {
        this.favoriteVenueRepository = favoriteVenueRepository;
        this.userRepository = userRepository;
    }

    /**
     * Add a favorite venue for a user
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
        return convertToDTO(savedVenue);
    }

    /**
     * Get all favorite venues for a user
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
     * Get favorite venue by ID
     */
    public FavoriteVenueDTO getFavoriteVenueById(Long id) {
        FavoriteVenue favoriteVenue = favoriteVenueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Favorite venue not found with ID: " + id
                ));
        return convertToDTO(favoriteVenue);
    }

    /**
     * Update favorite venue
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
        return convertToDTO(updatedVenue);
    }

    /**
     * Delete favorite venue
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
    }

    /**
     * Get default favorite venue for a user
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
     * Convert FavoriteVenue entity to FavoriteVenueDTO
     */
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