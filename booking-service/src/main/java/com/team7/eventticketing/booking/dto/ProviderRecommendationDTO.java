package com.team7.eventticketing.booking.dto;

public class ProviderRecommendationDTO {
    private Long providerId;
    private String name;
    private String specialty;
    private Long score;

    public ProviderRecommendationDTO() {
    }

    public ProviderRecommendationDTO(Long providerId, String name, String specialty, Long score) {
        this.providerId = providerId;
        this.name = name;
        this.specialty = specialty;
        this.score = score;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public Long getScore() {
        return score;
    }

    public void setScore(Long score) {
        this.score = score;
    }
}