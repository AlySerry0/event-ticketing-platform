package com.team7.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class UnusedTicketDTO {
    private Long ticketId;
    private String attendeeName;
    private String ticketCode;
    private Long bookingId;
    private String eventName;
    private LocalDateTime eventDate;

    private UnusedTicketDTO() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final UnusedTicketDTO dto = new UnusedTicketDTO();

        public Builder ticketId(Long ticketId) {
            dto.ticketId = ticketId;
            return this;
        }

        public Builder attendeeName(String attendeeName) {
            dto.attendeeName = attendeeName;
            return this;
        }

        public Builder ticketCode(String ticketCode) {
            dto.ticketCode = ticketCode;
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

        public Builder eventDate(LocalDateTime eventDate) {
            dto.eventDate = eventDate;
            return this;
        }

        public UnusedTicketDTO build() {
            return dto;
        }
    }

    public Long getTicketId() { return ticketId; }
    public String getAttendeeName() { return attendeeName; }
    public String getTicketCode() { return ticketCode; }
    public Long getBookingId() { return bookingId; }
    public String getEventName() { return eventName; }
    public LocalDateTime getEventDate() { return eventDate; }
}
