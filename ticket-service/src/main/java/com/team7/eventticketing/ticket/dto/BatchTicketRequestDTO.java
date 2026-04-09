package com.team7.eventticketing.ticket.dto;

import java.util.List;

public class BatchTicketRequestDTO {

    private Long bookingId;
    private List<IssueTicketDTO> tickets;

    public BatchTicketRequestDTO() {}

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public List<IssueTicketDTO> getTickets() { return tickets; }
    public void setTickets(List<IssueTicketDTO> tickets) { this.tickets = tickets; }
}
