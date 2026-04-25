package com.team7.eventticketing.user.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Set;

public class AuthContext {

    private final HttpServletRequest request;
    private String token;
    private String authenticatedEmail;
    private Long authenticatedUserId;
    private String authenticatedRole;
    private Set<String> allowedRoles = Collections.emptySet();

    public AuthContext(HttpServletRequest request) {
        this.request = request;
    }

    public HttpServletRequest getRequest()           { return request; }
    public String getToken()                         { return token; }
    public void setToken(String token)               { this.token = token; }
    public String getAuthenticatedEmail()            { return authenticatedEmail; }
    public void setAuthenticatedEmail(String email)  { this.authenticatedEmail = email; }
    public Long getAuthenticatedUserId()             { return authenticatedUserId; }
    public void setAuthenticatedUserId(Long id)      { this.authenticatedUserId = id; }
    public String getAuthenticatedRole()             { return authenticatedRole; }
    public void setAuthenticatedRole(String role)    { this.authenticatedRole = role; }
    public Set<String> getAllowedRoles()             { return allowedRoles; }
    public void setAllowedRoles(Set<String> roles)   { this.allowedRoles = roles; }
}