package com.team7.eventticketing.user.controller;

import com.team7.eventticketing.user.config.DataSeeder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeedController {

    private final DataSeeder dataSeeder;

    public SeedController(DataSeeder dataSeeder) {
        this.dataSeeder = dataSeeder;
    }

    @GetMapping("/api/seed")
    public ResponseEntity<Map<String, String>> seed() {
        boolean adminSeeded = dataSeeder.seedAdminAccount();
        boolean userSeeded = dataSeeder.seedUserAccount();
        return ResponseEntity.ok(Map.of(
                "admin", adminSeeded ? "SEEDED" : "SKIPPED, IT IS ALREADY SEEDED",
                "products", userSeeded ? "SEEDED" : "SKIPPED, IT IS ALREADY SEEDED"));
    }
}
