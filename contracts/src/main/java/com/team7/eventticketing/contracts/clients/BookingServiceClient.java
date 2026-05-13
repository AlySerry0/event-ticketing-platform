package com.team7.eventticketing.contracts.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url:http://booking-service:8080}")
public interface BookingServiceClient {

}