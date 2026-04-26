package com.team7.eventticketing.event.dto;

import java.util.List;

/**
 * DTO for Events with Unverified Sessions report (S2-F9).
 *
 * Builder Pattern retrofit (M2 requirement):
 * Has 5 fields → Builder required.
 *
 * Usage:
 *   EventSessionAlertDTO dto = EventSessionAlertDTO.builder()
 *       .eventId(1L)
 *       .eventName("Cairo Jazz")
 *       .eventStatus("UPCOMING")
 *       .unverifiedSessions(sessionList)
 *       .unverifiedCount(2)
 *       .build();
 */
public class EventSessionAlertDTO {

    private final Long eventId;
    private final String eventName;
    private final String eventStatus;
    private final List<EventSessionDTO> unverifiedSessions;
    private final Integer unverifiedCount;

    // Private constructor — only the Builder may call this
    private EventSessionAlertDTO(Builder builder) {
        this.eventId            = builder.eventId;
        this.eventName          = builder.eventName;
        this.eventStatus        = builder.eventStatus;
        this.unverifiedSessions = builder.unverifiedSessions;
        this.unverifiedCount    = builder.unverifiedCount;
    }

    // Legacy all-args constructor kept for backward compatibility
    public EventSessionAlertDTO(Long eventId, String eventName, String eventStatus,
                                List<EventSessionDTO> unverifiedSessions, Integer unverifiedCount) {
        this.eventId            = eventId;
        this.eventName          = eventName;
        this.eventStatus        = eventStatus;
        this.unverifiedSessions = unverifiedSessions;
        this.unverifiedCount    = unverifiedCount;
    }

    public EventSessionAlertDTO() {
        this(null, null, null, null, null);
    }

    // -----------------------------------------------------------------------
    // Builder entry point
    // -----------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    // -----------------------------------------------------------------------
    // Static inner Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private Long eventId;
        private String eventName;
        private String eventStatus;
        private List<EventSessionDTO> unverifiedSessions;
        private Integer unverifiedCount;

        private Builder() {}

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public Builder eventStatus(String eventStatus) {
            this.eventStatus = eventStatus;
            return this;
        }

        public Builder unverifiedSessions(List<EventSessionDTO> unverifiedSessions) {
            this.unverifiedSessions = unverifiedSessions;
            return this;
        }

        public Builder unverifiedCount(Integer unverifiedCount) {
            this.unverifiedCount = unverifiedCount;
            return this;
        }

        public EventSessionAlertDTO build() {
            return new EventSessionAlertDTO(this);
        }
    }

    // -----------------------------------------------------------------------
    // Getters (setters omitted — immutable via Builder)
    // -----------------------------------------------------------------------

    public Long getEventId()                              { return eventId; }
    public String getEventName()                          { return eventName; }
    public String getEventStatus()                        { return eventStatus; }
    public List<EventSessionDTO> getUnverifiedSessions()  { return unverifiedSessions; }
    public Integer getUnverifiedCount()                   { return unverifiedCount; }
}