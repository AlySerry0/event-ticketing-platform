package com.team7.eventticketing.booking.model.neo4j;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RelationshipProperties
public class AttendedRelationship {

    @RelationshipId
    private Long id;

    @TargetNode
    private EventNode event;

    private Integer attendanceCount;
    private LocalDateTime lastAttendedDate;
    private List<Long> recordedBookingIds = new ArrayList<>();

    public AttendedRelationship() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EventNode getEvent() {
        return event;
    }

    public void setEvent(EventNode event) {
        this.event = event;
    }

    public Integer getAttendanceCount() {
        return attendanceCount;
    }

    public void setAttendanceCount(Integer attendanceCount) {
        this.attendanceCount = attendanceCount;
    }

    public LocalDateTime getLastAttendedDate() {
        return lastAttendedDate;
    }

    public void setLastAttendedDate(LocalDateTime lastAttendedDate) {
        this.lastAttendedDate = lastAttendedDate;
    }

    public List<Long> getRecordedBookingIds() {
        return recordedBookingIds;
    }

    public void setRecordedBookingIds(List<Long> recordedBookingIds) {
        this.recordedBookingIds = recordedBookingIds;
    }
}
