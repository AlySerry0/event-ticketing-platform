package com.team7.eventticketing.event.dto;

public class TopEventDTO {

    private Long eventId;
    private String name;
    private Double rating;
    private Long totalBookings;

    public TopEventDTO(Long eventId, String name, Double rating, Long totalBookings) {
        this.eventId = eventId;
        this.name = name;
        this.rating = rating;
        this.totalBookings = totalBookings;
    }

    public Long getEventId() { return eventId; }
    public String getName() { return name; }
    public Double getRating() { return rating; }
    public Long getTotalBookings() { return totalBookings; }
}