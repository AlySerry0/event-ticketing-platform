package com.team7.eventticketing.booking.dto;

import java.time.LocalDateTime;

public class EventRecommendationDTO {

    private Long eventId;
    private String name;
    private String category;
    private LocalDateTime eventDate;
    private Long score;

    // 🔒 private constructor
    private EventRecommendationDTO(Builder builder) {
        this.eventId = builder.eventId;
        this.name = builder.name;
        this.category = builder.category;
        this.eventDate = builder.eventDate;
        this.score = builder.score;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public Long getScore() {
        return score;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public void setScore(Long score) {
        this.score = score;
    }

    // 🔥 Builder pattern
    public static class Builder {
        private Long eventId;
        private String name;
        private String category;
        private LocalDateTime eventDate;
        private Long score;

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder eventDate(LocalDateTime eventDate) {
            this.eventDate = eventDate;
            return this;
        }

        public Builder score(Long score) {
            this.score = score;
            return this;
        }

        public EventRecommendationDTO build() {
            return new EventRecommendationDTO(this);
        }

    }
}