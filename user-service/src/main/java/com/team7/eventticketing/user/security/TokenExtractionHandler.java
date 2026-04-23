package com.team7.eventticketing.user.security;

import org.springframework.http.HttpHeaders;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        String header = context.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return AuthResult.unauthorized("Missing or malformed Authorization header");
        }
        context.setToken(header.substring(7));
        return passToNext(context);
    }
}