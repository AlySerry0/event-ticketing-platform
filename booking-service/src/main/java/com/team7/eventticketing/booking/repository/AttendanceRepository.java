package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.adapter.Neo4jRecordAdapter;
import com.team7.eventticketing.booking.dto.EventRecommendationDTO;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AttendanceRepository {

    private static final String RECORD_ATTENDANCE_CYPHER = """
            MERGE (u:User {userId: $userId})
            ON CREATE SET u.name = $userName
            SET u:UserNode
            MERGE (e:Event {eventId: $eventId})
            SET e.name = $eventName, e.category = $category
            SET e:EventNode
            MERGE (u)-[r:ATTENDED]->(e)
            ON CREATE SET r.attendanceCount = 0, r.lastAttendedDate = $now, r.recordedBookingIds = []
            WITH r, $bookingId IN r.recordedBookingIds AS alreadyRecorded
            FOREACH (_ IN CASE WHEN NOT alreadyRecorded THEN [1] ELSE [] END |
              SET r.attendanceCount = r.attendanceCount + 1,
                  r.lastAttendedDate = $now,
                  r.recordedBookingIds = r.recordedBookingIds + [$bookingId]
            )
            RETURN r.attendanceCount AS attendanceCount, alreadyRecorded
            """;

    private static final String GET_RECOMMENDATIONS_CYPHER = """
            MATCH (target:User {userId: $userId})-[:ATTENDED]->(shared:Event)<-[:ATTENDED]-(similar:User)-[:ATTENDED]->(recommended:Event)
            WHERE NOT (target)-[:ATTENDED]->(recommended)
            RETURN recommended.eventId AS eventId,
                   recommended.name AS eventName,
                   recommended.category AS category,
                   count(similar) AS score
            ORDER BY score DESC
            LIMIT $limit
            """;

    @Autowired
    private Driver driver;

    @Autowired
    private Neo4jRecordAdapter neo4jRecordAdapter;

    public AttendanceResult recordAttendance(Map<String, Object> params) {
        try (var session = driver.session()) {
            var record = session.executeWrite(tx -> tx.run(RECORD_ATTENDANCE_CYPHER, params).single());
            return new AttendanceResult(
                    record.get("attendanceCount").asInt(),
                    record.get("alreadyRecorded").asBoolean());
        }
    }

    public List<EventRecommendationDTO> getRecommendations(Long userId, int limit) {
        try (var session = driver.session()) {
            return session
                    .executeRead(tx -> tx.run(GET_RECOMMENDATIONS_CYPHER, Map.of("userId", userId, "limit", limit))
                            .list(neo4jRecordAdapter::adapt));
        }
    }
}
