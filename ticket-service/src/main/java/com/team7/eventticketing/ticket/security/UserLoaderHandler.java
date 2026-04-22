package com.team7.eventticketing.ticket.security;

import com.team7.eventticketing.ticket.repository.TicketRepository;

public class UserLoaderHandler extends AuthHandler {

    private final TicketRepository ticketRepository;

    public UserLoaderHandler(TicketRepository repo) {
        this.ticketRepository = repo;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        if (!ticketRepository.userExistsByEmail(context.getAuthenticatedEmail())) {
            return AuthResult.unauthorized("User no longer exists");
        }
        return passToNext(context);
    }
}
