package com.team7.eventticketing.ticket.security;

import com.team7.eventticketing.ticket.security.AuthContext;
import com.team7.eventticketing.ticket.security.AuthHandler;
import com.team7.eventticketing.ticket.security.AuthResult;
import com.team7.eventticketing.ticket.service.JwtService;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        if (!jwtService.isTokenValid(context.getToken())) {
            return AuthResult.unauthorized("Invalid or expired JWT token");
        }
        // Populate context from claims so later handlers don't re-parse
        context.setAuthenticatedEmail(jwtService.extractEmail(context.getToken()));
        context.setAuthenticatedUserId(jwtService.extractUserId(context.getToken()));
        context.setAuthenticatedRole(jwtService.extractRole(context.getToken()));
        return passToNext(context);
    }
}