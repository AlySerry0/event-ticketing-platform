package com.team7.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class UnusedTicketDTO {
    private Long ticketId;
    private String attendeeName;
    private String ticketCode;
    private Long bookingId;
    private String eventName;
    private LocalDateTime eventDate;

    public UnusedTicketDTO(Long ticketId, String attendeeName, String ticketCode,
                           Long bookingId, String eventName, LocalDateTime eventDate) {
        this.ticketId = ticketId;
        this.attendeeName = attendeeName;
        this.ticketCode = ticketCode;
        this.bookingId = bookingId;
        this.eventName = eventName;
        this.eventDate = eventDate;
    }

    public Long getTicketId() { return ticketId; }
    public String getAttendeeName() { return attendeeName; }
    public String getTicketCode() { return ticketCode; }
    public Long getBookingId() { return bookingId; }
    public String getEventName() { return eventName; }
    public LocalDateTime getEventDate() { return eventDate; }

    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public void setEventDate(LocalDateTime eventDate) { this.eventDate = eventDate; }
}
