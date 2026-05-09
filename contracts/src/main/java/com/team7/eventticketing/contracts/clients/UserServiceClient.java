package com.team7.eventticketing.contracts.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service", url = "${services.user.url:http://user-service:8080}")
public interface UserServiceClient {

}