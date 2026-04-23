package com.team7.eventticketing.event.security;

import com.team7.eventticketing.event.repository.EventRepository;

public class UserLoaderHandler extends AuthHandler {

    private final EventRepository eventRepository;

    public UserLoaderHandler(EventRepository repo) {
        this.eventRepository = repo;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        if (!eventRepository.userExistsByEmail(context.getAuthenticatedEmail())) {
            return AuthResult.unauthorized("User no longer exists");
        }
        return passToNext(context);
    }
}
