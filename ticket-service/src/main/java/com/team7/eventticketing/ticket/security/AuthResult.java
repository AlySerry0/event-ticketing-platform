package com.team7.eventticketing.ticket.security;

public class AuthResult {

    private final boolean success;
    private final int statusCode;   // 401 or 403 on failure
    private final String message;

    private AuthResult(boolean success, int statusCode, String message) {
        this.success = success;
        this.statusCode = statusCode;
        this.message = message;
    }

    public static AuthResult success() {
        return new AuthResult(true, 200, null);
    }

    public static AuthResult unauthorized(String message) {
        return new AuthResult(false, 401, message);
    }

    public static AuthResult forbidden(String message) {
        return new AuthResult(false, 403, message);
    }

    public boolean isSuccess()    { return success; }
    public int getStatusCode()    { return statusCode; }
    public String getMessage()    { return message; }
}