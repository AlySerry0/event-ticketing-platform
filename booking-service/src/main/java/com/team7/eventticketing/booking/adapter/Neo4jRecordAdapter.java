package com.team7.eventticketing.booking.adapter;

import com.team7.eventticketing.booking.dto.EventRecommendationDTO;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public EventRecommendationDTO adapt(Record record) {
        return new EventRecommendationDTO(
                record.get("eventId").asLong(),
                record.get("eventName").asString(""),
                record.get("category").asString(""),
                record.get("score").asLong()
        );
    }
}