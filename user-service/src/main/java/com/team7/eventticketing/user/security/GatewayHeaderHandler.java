package com.team7.eventticketing.user.security;

public class GatewayHeaderHandler extends AuthHandler {
    @Override
    public AuthResult handle(AuthContext ctx) {
        // Headers forwarded by Spring Cloud Gateway
        String userIdHeader = ctx.getRequest().getHeader("X-User-Id");
        String roleHeader = ctx.getRequest().getHeader("X-User-Role");

        if (userIdHeader != null && roleHeader != null) {
            // Gateway already validated the JWT signature [cite: 21, 243]; skip re-validation.
            // Still enforce per-endpoint role rules — that is a separate concern from JWT validity.
            ctx.setAuthenticatedUserId(Long.parseLong(userIdHeader));
            ctx.setAuthenticatedRole(roleHeader);

            java.util.Set<String> allowed = ctx.getAllowedRoles();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(roleHeader)) {
                return AuthResult.forbidden("Insufficient role. Allowed: " + allowed);
            }
            return AuthResult.success();
        }

        // If headers are missing (e.g., local dev or internal call), move to TokenExtractionHandler
        return passToNext(ctx);
    }
}