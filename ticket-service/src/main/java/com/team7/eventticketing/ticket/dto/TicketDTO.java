package com.team7.eventticketing.ticket.dto;

import com.team7.eventticketing.ticket.model.TicketStatus;
import java.time.LocalDateTime;
import java.util.Map;

public class TicketDTO {
    private Long id;
    private Long bookingId;
    private Long eventId;
    private String attendeeName;
    private String ticketCode;
    private TicketStatus status;
    private LocalDateTime issuedAt;
    private Map<String, Object> metadata;

    public TicketDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getAttendeeName() { return attendeeName; }
    public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
