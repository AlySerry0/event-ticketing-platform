package com.team7.eventticketing.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class JwtGatewayFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // 1. Auth Bypass 
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 2. Extract Token and Correlation ID [cite: 1263-1264, 1290]
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();

        // 3. Logic: Re-use your M2 utility class for token validation [cite: 1274]
        // If invalid, return exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        
        String uid = "extracted-id"; // TODO: extract from JWT
        String role = "extracted-role"; // TODO: extract from JWT

        // 4. Mutate and Forward [cite: 1267-1273]
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", uid)
                .header("X-User-Role", role)
                .header("X-Correlation-ID", correlationId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}