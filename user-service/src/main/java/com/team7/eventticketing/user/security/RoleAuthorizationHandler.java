package com.team7.eventticketing.user.security;

import java.util.Set;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        Set<String> allowed = context.getAllowedRoles();
        if (allowed == null || allowed.isEmpty()) {
            return AuthResult.forbidden("No roles declared for this endpoint");
        }
        if (!allowed.contains(context.getAuthenticatedRole())) {
            return AuthResult.forbidden("Insufficient role. Allowed: " + allowed);
        }
        return passToNext(context);
    }
}