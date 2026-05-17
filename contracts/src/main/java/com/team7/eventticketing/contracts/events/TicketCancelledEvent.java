package com.team7.eventticketing.contracts.events;

public record TicketCancelledEvent(Long ticketId, Long bookingId) {}
