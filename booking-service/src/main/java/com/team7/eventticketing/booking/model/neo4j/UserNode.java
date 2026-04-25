package com.team7.eventticketing.booking.model.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("User")
public class UserNode {
    @Id
    private Long userId;
    private String name;

    @Relationship(type = "ATTENDED", direction = Relationship.Direction.OUTGOING)
    private Set<AttendedRelationship> attendedEvents = new HashSet<>();

    public UserNode() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<AttendedRelationship> getAttendedEvents() {
        return attendedEvents;
    }

    public void setAttendedEvents(Set<AttendedRelationship> attendedEvents) {
        this.attendedEvents = attendedEvents;
    }
}
