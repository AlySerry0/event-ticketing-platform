package com.team7.eventticketing.user.security;

import com.team7.eventticketing.user.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enforces resource-level ownership for endpoints scoped to a specific user.
 *
 * Rule: the caller must be the target user (their JWT uid matches targetUserId)
 * OR have role ADMIN. Otherwise 403.
 *
 * The JwtAuthenticationFilter has already validated the token by the time this
 * runs; we re-parse to extract uid + role since the SecurityContext only exposes
 * email + role, not the user id.
 */
@Component
public class OwnershipChecker {

    private final JwtService jwtService;

    public OwnershipChecker(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public void requireOwnerOrAdmin(Long targetUserId, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header");
        }
        String token = authHeader.substring(7);

        Long callerUid;
        String callerRole;
        try {
            callerUid = jwtService.extractUserId(token);
            callerRole = jwtService.extractRole(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token");
        }

        boolean isOwner = callerUid != null && callerUid.equals(targetUserId);
        boolean isAdmin = "ADMIN".equals(callerRole);

        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you can only access your own data");
        }
    }
}