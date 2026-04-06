package com.team7.eventticketing.user.repository;

import com.team7.eventticketing.user.model.FavoriteVenue;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteVenueRepository extends JpaRepository<FavoriteVenue, Long> {
    List<FavoriteVenue> findByUserId(Long userId);
    FavoriteVenue findByUserIdAndIsDefaultTrue(Long userId);

    // Find a venue by ID and userId to verify ownership
    Optional<FavoriteVenue> findByIdAndUserId(Long id, Long userId);

    // Reset all defaults for a user
    @Modifying
    @Transactional
    @Query("UPDATE FavoriteVenue f SET f.isDefault = false WHERE f.user.id = :userId")
    void resetAllDefaultsForUser(@Param("userId") Long userId);
}


