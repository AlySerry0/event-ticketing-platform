package com.team7.eventticketing.contracts.feign;

import com.team7.eventticketing.contracts.dto.EventTicketSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", url = "${feign.ticket-service.url:http://ticket-service:8080}")
public interface TicketServiceClient {

    @GetMapping("/api/tickets/booking/{bookingId}/used-count")
    int getUsedTicketCount(@PathVariable Long bookingId);

    @GetMapping("/api/tickets/event/{eventId}/summary")
    EventTicketSummaryDTO getEventTicketSummary(@PathVariable Long eventId);
}
