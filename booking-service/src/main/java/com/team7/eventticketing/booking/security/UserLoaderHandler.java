package com.team7.eventticketing.booking.security;

import com.team7.eventticketing.contracts.feign.UserServiceClient;
import feign.FeignException;

public class UserLoaderHandler extends AuthHandler {

    private final UserServiceClient userServiceClient;

    public UserLoaderHandler(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        try {
            userServiceClient.getUser(context.getAuthenticatedUserId());
        } catch (FeignException.NotFound e) {
            return AuthResult.unauthorized("User no longer exists");
        }
        return passToNext(context);
    }
}
