package com.team7.eventticketing.ticket.dto;

public class NearbyTicketDTO {
    private Long ticketId;
    private String attendeeName;
    private Long bookingId;
    private String eventName;
    private Double eventLat;
    private Double eventLon;
    private Double distanceKm;

    public NearbyTicketDTO() {}

    public NearbyTicketDTO(Long ticketId, String attendeeName, Long bookingId, String eventName, Double eventLat, Double eventLon, Double distanceKm) {
        this.ticketId = ticketId;
        this.attendeeName = attendeeName;
        this.bookingId = bookingId;
        this.eventName = eventName;
        this.eventLat = eventLat;
        this.eventLon = eventLon;
        this.distanceKm = distanceKm;
    }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public String getAttendeeName() { return attendeeName; }
    public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public Double getEventLat() { return eventLat; }
    public void setEventLat(Double eventLat) { this.eventLat = eventLat; }

    public Double getEventLon() { return eventLon; }
    public void setEventLon(Double eventLon) { this.eventLon = eventLon; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
}
