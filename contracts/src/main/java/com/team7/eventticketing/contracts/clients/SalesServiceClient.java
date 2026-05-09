package com.team7.eventticketing.contracts.clients;

import com.team7.eventticketing.contracts.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "sales-service", url = "${services.sales.url:http://sales-service:8080}")
public interface SalesServiceClient {

}