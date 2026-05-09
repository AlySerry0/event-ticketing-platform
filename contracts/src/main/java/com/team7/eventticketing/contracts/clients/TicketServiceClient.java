package com.team7.eventticketing.contracts.clients;

import com.team7.eventticketing.contracts.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "ticket-service", url = "${services.ticket.url:http://ticket-service:8080}")
public interface TicketServiceClient {

}