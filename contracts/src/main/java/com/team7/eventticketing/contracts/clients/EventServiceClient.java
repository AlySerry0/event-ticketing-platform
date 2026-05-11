package com.team7.eventticketing.contracts.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "event-service", url = "${services.event.url:http://event-service:8080}")
public interface EventServiceClient {

}