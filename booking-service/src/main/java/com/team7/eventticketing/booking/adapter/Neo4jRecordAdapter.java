package com.team7.eventticketing.booking.adapter;

import com.team7.eventticketing.booking.dto.EventRecommendationDTO;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public EventRecommendationDTO adapt(Record record) {
        return new EventRecommendationDTO.Builder()
                .eventId(record.get("eventId").asLong())
                .name(record.get("eventName").asString(""))
                .category(record.get("category").asString(""))
                .eventDate(null) // still filled later from PostgreSQL
                .score(record.get("score").asLong())
                .build();
    }
}