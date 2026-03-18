package com.team7.eventticketing.booking.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/bookings/health")
@RestController
public class HealthController {

    @RequestMapping("")
    public String health() {
        return "OK";
    }
}
