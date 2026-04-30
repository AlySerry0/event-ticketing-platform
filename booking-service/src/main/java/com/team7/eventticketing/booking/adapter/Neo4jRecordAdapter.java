package com.team7.eventticketing.booking.adapter;

import com.team7.eventticketing.booking.dto.ProviderRecommendationDTO;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public ProviderRecommendationDTO adapt(Record record) {
        return new ProviderRecommendationDTO(
                record.get("eventId").asLong(),     // temporarily mapped as providerId
                record.get("eventName").asString(""),
                record.get("category").asString(""),
                record.get("score").asLong()
        );
    }
}