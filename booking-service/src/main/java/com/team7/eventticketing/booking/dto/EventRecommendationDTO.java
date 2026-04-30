package com.team7.eventticketing.booking.dto;

public class EventRecommendationDTO {
    private Long eventId;
    private String eventName;
    private String category;
    private Long score;

    public EventRecommendationDTO() {
    }

    public EventRecommendationDTO(Long eventId, String eventName, String category, Long score) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.category = category;
        this.score = score;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getScore() {
        return score;
    }

    public void setScore(Long score) {
        this.score = score;
    }
}