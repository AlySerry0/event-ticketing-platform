package com.team7.eventticketing.event.dto;

/**
 * DTO for Top Rated Events Report (S2-F6).
 *
 * Builder Pattern retrofit (M2 requirement):
 * Has 4 fields — included because TopEventDTO is returned by a report endpoint
 * and used by the Adapter pattern (TopEventAdapter). The spec includes S2-F6
 * in the Builder scope.
 *
 * Usage:
 *   TopEventDTO dto = TopEventDTO.builder()
 *       .eventId(1L)
 *       .name("Cairo Jazz")
 *       .rating(4.9)
 *       .totalBookings(20L)
 *       .build();
 */
public class TopEventDTO {

    private final Long eventId;
    private final String name;
    private final Double rating;
    private final Long totalBookings;

    // Private constructor — only the Builder may call this
    private TopEventDTO(Builder builder) {
        this.eventId       = builder.eventId;
        this.name          = builder.name;
        this.rating        = builder.rating;
        this.totalBookings = builder.totalBookings;
    }

    public TopEventDTO() {
        this.eventId = null;
        this.name = null;
        this.rating = null;
        this.totalBookings = null;
    }

    // Legacy all-args constructor kept for backward compatibility
    public TopEventDTO(Long eventId, String name, Double rating, Long totalBookings) {
        this.eventId       = eventId;
        this.name          = name;
        this.rating        = rating;
        this.totalBookings = totalBookings;
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
        private String name;
        private Double rating;
        private Long totalBookings;

        private Builder() {}

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder rating(Double rating) {
            this.rating = rating;
            return this;
        }

        public Builder totalBookings(Long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public TopEventDTO build() {
            return new TopEventDTO(this);
        }
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public Long getEventId()        { return eventId; }
    public String getName()         { return name; }
    public Double getRating()       { return rating; }
    public Long getTotalBookings()  { return totalBookings; }
}