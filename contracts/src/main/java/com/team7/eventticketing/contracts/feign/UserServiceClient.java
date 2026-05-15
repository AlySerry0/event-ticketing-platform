package com.team7.eventticketing.contracts.feign;

import com.team7.eventticketing.contracts.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${feign.user-service.url:http://user-service:8080}")
public interface UserServiceClient {
    @GetMapping("/api/users/{userId}")
    UserDTO getUser(@PathVariable("userId") Long userId);
}