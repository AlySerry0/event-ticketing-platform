package com.team7.eventticketing.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "user-service", url = "${services.user.url:http://user-service:8080}")
public interface UserServiceClient {

}