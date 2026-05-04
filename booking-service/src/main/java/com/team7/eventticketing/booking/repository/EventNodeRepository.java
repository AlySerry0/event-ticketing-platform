package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.model.neo4j.EventNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventNodeRepository extends Neo4jRepository<EventNode, Long> {
    Optional<EventNode> findByEventId(Long eventId);
}
