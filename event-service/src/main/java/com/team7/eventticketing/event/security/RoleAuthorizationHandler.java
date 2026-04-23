package com.team7.eventticketing.event.security;

import com.team7.eventticketing.event.security.AuthContext;
import com.team7.eventticketing.event.security.AuthHandler;
import com.team7.eventticketing.event.security.AuthResult;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        String required = context.getRequiredRole();
        if (required == null) {
            // No specific role required — any authenticated user passes
            return passToNext(context);
        }
        if (!required.equals(context.getAuthenticatedRole())) {
            return AuthResult.forbidden("Insufficient role. Required: " + required);
        }
        return passToNext(context);
    }
}