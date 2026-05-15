package com.team7.eventticketing.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url:http://booking-service:8080}")
public interface BookingServiceClient {

}