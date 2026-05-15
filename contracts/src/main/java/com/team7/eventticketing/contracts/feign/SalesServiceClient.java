package com.team7.eventticketing.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "sales-service", url = "${feign.sales-service.url:http://sales-service:8080}")
public interface SalesServiceClient {

}