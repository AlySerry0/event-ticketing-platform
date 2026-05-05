package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.ActivityEventDTO;
import com.team7.eventticketing.user.dto.ActivityFeedDTO;
import com.team7.eventticketing.user.model.AuthEvent;
import com.team7.eventticketing.user.repository.AuthEventRepository;
import com.team7.eventticketing.user.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ActivityFeedService {

    private final AuthEventRepository authEventRepository;
    private final UserRepository userRepository;

    public ActivityFeedService(AuthEventRepository authEventRepository,
                               UserRepository userRepository) {
        this.authEventRepository = authEventRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns a paginated activity feed for the given user.
     *
     * Ownership is enforced at the controller level before this method is called.
     * This method only handles the MongoDB query, pagination, and caching.
     *
     * Cache key: user-service::S1-F12::{userId}::{page}::{size}
     * TTL: 5 minutes (configured in RedisConfig)
     *
     * @param userId the target user's ID
     * @param page   zero-based page number (default 0)
     * @param size   page size, capped at 100 (default 10)
     * @return paginated ActivityFeedDTO
     */
    @Cacheable(
            cacheNames = "S1-F12",
            key = "#userId + '::' + #page + '::' + #size"
    )
    public ActivityFeedDTO getActivityFeed(Long userId, int page, int size) {

        // Verify user exists in PostgreSQL — 404 if not
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        if (page < 0 || size < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Page and size must be positive integers");
        }

        // Cap size at 100 per spec Section 10.1.3
        int cappedSize = Math.min(size, 100);

        // Let Spring Data handle pagination and sorting — no manual subList needed
        Pageable pageable = PageRequest.of(page, cappedSize);

        Page<AuthEvent> resultPage = authEventRepository
                .findByUserIdOrderByTimestampDesc(userId, pageable);

        List<ActivityEventDTO> content = resultPage.getContent()
                .stream()
                .map(event -> new ActivityEventDTO(
                        event.getAction(),
                        event.getTimestamp(),
                        event.getDetails()
                ))
                .toList();

        return new ActivityFeedDTO(
                content,
                resultPage.getNumber(),        // actual page number
                resultPage.getSize(),          // actual page size used
                resultPage.getTotalElements()  // total across all pages
        );
    }
}