package com.team7.eventticketing.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "event-service", url = "${services.event.url:http://event-service:8080}")
public interface EventServiceClient {

}