package com.team7.eventticketing.user.repository;

import com.team7.eventticketing.user.model.AuthEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for AuthEvent documents.
 * Stored in the auth_events collection.
 *
 * Used by:
 * - MongoEventLogger to persist new audit events
 * - UserActivityService (S1-F12) to query the activity feed
 */
@Repository
public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {

    /**
     * Finds all events for a given user sorted by most recent first.
     * Used by S1-F12 Get User Activity Feed.
     *
     * @param userId   the PostgreSQL User.id to filter by
     * @param pageable pagination and sorting config
     * @return paginated list of auth events
     */
    Page<AuthEvent> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
}