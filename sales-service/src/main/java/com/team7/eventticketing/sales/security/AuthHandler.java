package com.team7.eventticketing.sales.security;

import com.team7.eventticketing.sales.security.AuthResult;

public abstract class AuthHandler {

    private AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next; // fluent chaining: a.setNext(b).setNext(c)
    }

    public abstract AuthResult handle(AuthContext context);

    protected AuthResult passToNext(AuthContext context) {
        if (next != null) {
            return next.handle(context);
        }
        return AuthResult.success();
    }
}