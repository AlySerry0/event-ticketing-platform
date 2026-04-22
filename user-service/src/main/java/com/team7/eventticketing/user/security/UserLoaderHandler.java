package com.team7.eventticketing.user.security;

import com.team7.eventticketing.user.repository.UserRepository;

public class UserLoaderHandler extends AuthHandler {

    private final UserRepository userRepository;

    public UserLoaderHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        boolean userExists = userRepository
                .existsByEmail(context.getAuthenticatedEmail());
        if (!userExists) {
            return AuthResult.unauthorized("User no longer exists");
        }
        return passToNext(context);
    }
}