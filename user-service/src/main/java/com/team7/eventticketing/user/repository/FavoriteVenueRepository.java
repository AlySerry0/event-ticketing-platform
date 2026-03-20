package com.team7.eventticketing.user.repository;

import com.team7.eventticketing.user.model.FavoriteVenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteVenueRepository extends JpaRepository<FavoriteVenue, Long> {
    List<FavoriteVenue> findByUserId(Long userId);
    FavoriteVenue findByUserIdAndIsDefaultTrue(Long userId);
}


