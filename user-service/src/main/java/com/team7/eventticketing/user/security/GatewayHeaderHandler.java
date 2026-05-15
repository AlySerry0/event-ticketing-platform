package com.team7.eventticketing.user.security;

public class GatewayHeaderHandler extends AuthHandler {
    @Override
    public AuthResult handle(AuthContext ctx) {
        // Headers forwarded by Spring Cloud Gateway 
        String userIdHeader = ctx.getRequest().getHeader("X-User-Id");
        String roleHeader = ctx.getRequest().getHeader("X-User-Role");

        if (userIdHeader != null && roleHeader != null) {
            // If Gateway sent these, it already validated the JWT [cite: 21, 243]
            ctx.setAuthenticatedUserId(Long.parseLong(userIdHeader));
            ctx.setAuthenticatedRole(roleHeader);
            
            // Return success immediately to bypass the rest of the JWT chain
            return AuthResult.success();
        }

        // If headers are missing (e.g., local dev or internal call), move to TokenExtractionHandler
        return passToNext(ctx);
    }
}