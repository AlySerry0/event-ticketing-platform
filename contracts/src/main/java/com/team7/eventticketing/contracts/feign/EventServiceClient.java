package com.team7.eventticketing.contracts.feign;

import com.team7.eventticketing.contracts.dto.EventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "event-service", url = "${feign.event-service.url:http://event-service:8080}")
public interface EventServiceClient {
    @GetMapping("/api/events/{id}")
    EventDTO getEvent(@PathVariable("id") Long id);
}