package com.team7.eventticketing.ticket.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UnusedTicketDTO {

    private Long ticketId;
    private String attendeeName;
    private String ticketCode;
    private Long bookingId;
    private String eventName;
    private LocalDateTime eventDate;

}