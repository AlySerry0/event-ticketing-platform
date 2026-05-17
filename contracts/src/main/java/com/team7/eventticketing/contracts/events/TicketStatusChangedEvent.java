package com.team7.eventticketing.contracts.events;

public record TicketStatusChangedEvent(Long ticketId, Long bookingId, String newStatus) {}
