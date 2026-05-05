package com.team7.eventticketing.ticket.dto;

public class NearbyTicketDTO {
    private Long ticketId;
    private String attendeeName;
    private Long bookingId;
    private String eventName;
    private Double eventLat;
    private Double eventLon;
    private Double distanceKm;

    private NearbyTicketDTO() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final NearbyTicketDTO dto = new NearbyTicketDTO();

        public Builder ticketId(Long ticketId) {
            dto.ticketId = ticketId;
            return this;
        }

        public Builder attendeeName(String attendeeName) {
            dto.attendeeName = attendeeName;
            return this;
        }

        public Builder bookingId(Long bookingId) {
            dto.bookingId = bookingId;
            return this;
        }

        public Builder eventName(String eventName) {
            dto.eventName = eventName;
            return this;
        }

        public Builder eventLat(Double eventLat) {
            dto.eventLat = eventLat;
            return this;
        }

        public Builder eventLon(Double eventLon) {
            dto.eventLon = eventLon;
            return this;
        }

        public Builder distanceKm(Double distanceKm) {
            dto.distanceKm = distanceKm;
            return this;
        }

        public NearbyTicketDTO build() {
            return dto;
        }
    }

    public Long getTicketId() { return ticketId; }
    public String getAttendeeName() { return attendeeName; }
    public Long getBookingId() { return bookingId; }
    public String getEventName() { return eventName; }
    public Double getEventLat() { return eventLat; }
    public Double getEventLon() { return eventLon; }
    public Double getDistanceKm() { return distanceKm; }
}