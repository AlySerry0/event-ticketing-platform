package com.team7.eventticketing.event.service;

import com.team7.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team7.eventticketing.contracts.dto.EventTicketSummaryDTO;
import com.team7.eventticketing.contracts.feign.BookingServiceClient;
import com.team7.eventticketing.contracts.feign.TicketServiceClient;
import com.team7.eventticketing.event.dto.EventDashboardDTO;
import com.team7.eventticketing.event.model.Event;
import com.team7.eventticketing.event.repository.EventRepository;
import feign.FeignException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventCacheService {

    private final EventRepository eventRepository;
    private final BookingServiceClient bookingServiceClient;
    private final TicketServiceClient ticketServiceClient;

    public EventCacheService(EventRepository eventRepository,
                             BookingServiceClient bookingServiceClient,
                             TicketServiceClient ticketServiceClient) {
        this.eventRepository = eventRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.ticketServiceClient = ticketServiceClient;
    }

    @Cacheable(value = "S2-F12", key = "#eventId")
    public EventDashboardDTO getEventDashboardCached(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found with id: " + eventId));

        long totalBookings = 0L;
        double totalRevenue = 0.0;
        long totalTicketsSold = 0L;
        long usedTickets = 0L;

        try {
            EventBookingRevenueDTO revenue =
                    bookingServiceClient.getEventRevenue(eventId, "2000-01-01", "2100-01-01");
            totalBookings = revenue.totalBookings();
            totalRevenue = revenue.totalRevenue() != null ? revenue.totalRevenue().doubleValue() : 0.0;
        } catch (FeignException e) {
            // booking-service unavailable — return zeros for revenue
        }

        try {
            EventTicketSummaryDTO tickets = ticketServiceClient.getEventTicketSummary(eventId);
            totalTicketsSold = tickets.totalTicketsSold();
            usedTickets = tickets.usedCount();
        } catch (FeignException e) {
            // ticket-service unavailable — return zeros for tickets
        }

        double attendanceRate = totalTicketsSold == 0
                ? 0.0 : (double) usedTickets / totalTicketsSold;

        return EventDashboardDTO.builder()
                .eventId(event.getId())
                .name(event.getName())
                .totalBookings(totalBookings)
                .totalTicketsSold(totalTicketsSold)
                .totalRevenue(totalRevenue)
                .averageAttendanceRate(attendanceRate)
                .averageRating(event.getRating() == null ? 0.0 : event.getRating())
                .build();
    }
}
