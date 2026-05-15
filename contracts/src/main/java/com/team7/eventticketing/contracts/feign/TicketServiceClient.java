package com.team7.eventticketing.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "ticket-service", url = "${feign.ticket-service.url:http://ticket-service:8080}")
public interface TicketServiceClient {

}