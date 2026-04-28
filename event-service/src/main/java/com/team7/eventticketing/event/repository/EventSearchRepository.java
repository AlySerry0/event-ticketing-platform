package com.team7.eventticketing.event.repository;

import com.team7.eventticketing.event.elasticsearch.EventSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface EventSearchRepository
        extends ElasticsearchRepository<EventSearchDocument, Long> {
    // No custom methods needed for F11 — save() is enough
}