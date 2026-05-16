package com.team7.eventticketing.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@EnableFeignClients(basePackages = "com.team7.eventticketing.contracts.feign")
@SpringBootApplication
public class SalesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesServiceApplication.class, args);
    }
}