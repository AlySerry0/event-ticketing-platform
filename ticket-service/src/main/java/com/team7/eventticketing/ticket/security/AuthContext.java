package com.team7.eventticketing.ticket.security;

import jakarta.servlet.http.HttpServletRequest;

public class AuthContext {

    private final HttpServletRequest request;
    private String token;
    private String authenticatedEmail;
    private Long authenticatedUserId;
    private String authenticatedRole;
    private String requiredRole; // null means any authenticated user is OK

    public AuthContext(HttpServletRequest request) {
        this.request = request;
    }

    // --- getters and setters ---

    public HttpServletRequest getRequest()           { return request; }
    public String getToken()                         { return token; }
    public void setToken(String token)               { this.token = token; }
    public String getAuthenticatedEmail()            { return authenticatedEmail; }
    public void setAuthenticatedEmail(String email)  { this.authenticatedEmail = email; }
    public Long getAuthenticatedUserId()             { return authenticatedUserId; }
    public void setAuthenticatedUserId(Long id)      { this.authenticatedUserId = id; }
    public String getAuthenticatedRole()             { return authenticatedRole; }
    public void setAuthenticatedRole(String role)    { this.authenticatedRole = role; }
    public String getRequiredRole()                  { return requiredRole; }
    public void setRequiredRole(String role)         { this.requiredRole = role; }
}