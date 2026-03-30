package com.team7.eventticketing.user.controller;

import com.team7.eventticketing.user.dto.FavoriteVenueDTO;
import com.team7.eventticketing.user.service.FavoriteVenueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/venues")
public class FavoriteVenueController {

    private final FavoriteVenueService favoriteVenueService;

    public FavoriteVenueController(FavoriteVenueService favoriteVenueService) {
        this.favoriteVenueService = favoriteVenueService;
    }

    /**
     * Add a favorite venue for a user
     * POST /api/users/{userId}/venues
     */
    @PostMapping
    public ResponseEntity<FavoriteVenueDTO> addFavoriteVenue(
            @PathVariable Long userId,
            @RequestBody FavoriteVenueDTO favoriteVenueDTO) {
        FavoriteVenueDTO createdVenue = favoriteVenueService.addFavoriteVenue(userId, favoriteVenueDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdVenue);
    }

    /**
     * Get all favorite venues for a user
     * GET /api/users/{userId}/venues
     */
    @GetMapping
    public ResponseEntity<List<FavoriteVenueDTO>> getUserFavoriteVenues(@PathVariable Long userId) {
        List<FavoriteVenueDTO> venues = favoriteVenueService.getUserFavoriteVenues(userId);
        return ResponseEntity.ok(venues);
    }

    /**
     * Get favorite venue by ID
     * GET /api/users/{userId}/venues/{venueId}
     */
    @GetMapping("/{venueId}")
    public ResponseEntity<FavoriteVenueDTO> getFavoriteVenueById(
            @PathVariable Long userId,
            @PathVariable Long venueId) {
        FavoriteVenueDTO venue = favoriteVenueService.getFavoriteVenueById(venueId);
        return ResponseEntity.ok(venue);
    }

    /**
     * Update favorite venue
     * PUT /api/users/{userId}/venues/{venueId}
     */
    @PutMapping("/{venueId}")
    public ResponseEntity<FavoriteVenueDTO> updateFavoriteVenue(
            @PathVariable Long userId,
            @PathVariable Long venueId,
            @RequestBody FavoriteVenueDTO favoriteVenueDTO) {
        FavoriteVenueDTO updatedVenue = favoriteVenueService.updateFavoriteVenue(venueId, favoriteVenueDTO);
        return ResponseEntity.ok(updatedVenue);
    }

    /**
     * Delete favorite venue
     * DELETE /api/users/{userId}/venues/{venueId}
     */
    @DeleteMapping("/{venueId}")
    public ResponseEntity<Void> deleteFavoriteVenue(
            @PathVariable Long userId,
            @PathVariable Long venueId) {
        favoriteVenueService.deleteFavoriteVenue(venueId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get default favorite venue for a user
     * GET /api/users/{userId}/venues/default
     */
    @GetMapping("/default")
    public ResponseEntity<FavoriteVenueDTO> getDefaultFavoriteVenue(@PathVariable Long userId) {
        FavoriteVenueDTO venue = favoriteVenueService.getDefaultFavoriteVenue(userId);
        return ResponseEntity.ok(venue);
    }
}