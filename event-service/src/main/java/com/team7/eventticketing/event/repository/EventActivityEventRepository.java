package com.team7.eventticketing.event.repository;

import com.team7.eventticketing.event.model.EventActivityEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data MongoDB repository for EventActivityEvent documents.
 */
@Repository
public interface EventActivityEventRepository extends MongoRepository<EventActivityEvent, String> {

    List<EventActivityEvent> findByEventIdOrderByTimestampDesc(Long eventId);

    List<EventActivityEvent> findByAction(String action);
}