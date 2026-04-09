package com.team7.eventticketing.ticket.dto;

import java.util.Map;

public class IssueTicketDTO {
    private String attendeeName;
    private String ticketCode;
    private Map<String, Object> metadata;

    public IssueTicketDTO() {}

    public String getAttendeeName() { return attendeeName; }
    public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
