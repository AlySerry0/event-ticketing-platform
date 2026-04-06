package com.team7.eventticketing.event.dto;

import java.util.List;

public class EventSessionAlertDTO {

    private Long eventId;
    private String eventName;
    private String eventStatus;
    private List<EventSessionDTO> unverifiedSessions;
    private Integer unverifiedCount;

    public EventSessionAlertDTO() {
    }

    public EventSessionAlertDTO(Long eventId,
                                String eventName,
                                String eventStatus,
                                List<EventSessionDTO> unverifiedSessions,
                                Integer unverifiedCount) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventStatus = eventStatus;
        this.unverifiedSessions = unverifiedSessions;
        this.unverifiedCount = unverifiedCount;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public List<EventSessionDTO> getUnverifiedSessions() {
        return unverifiedSessions;
    }

    public void setUnverifiedSessions(List<EventSessionDTO> unverifiedSessions) {
        this.unverifiedSessions = unverifiedSessions;
    }

    public Integer getUnverifiedCount() {
        return unverifiedCount;
    }

    public void setUnverifiedCount(Integer unverifiedCount) {
        this.unverifiedCount = unverifiedCount;
    }
}