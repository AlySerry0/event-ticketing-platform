package com.team7.eventticketing.contracts.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "ticket-service", url = "${feign.ticket-service:http://ticket-service:8080}")
public interface TicketServiceClient {

}