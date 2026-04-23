package com.team7.eventticketing.sales.security;

import com.team7.eventticketing.sales.repository.TicketSaleRepository;

public class UserLoaderHandler extends AuthHandler {

    private final TicketSaleRepository ticketSalesRepository;

    public UserLoaderHandler(TicketSaleRepository repo) {
        this.ticketSalesRepository = repo;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        if (!ticketSalesRepository.userExistsByEmail(context.getAuthenticatedEmail())) {
            return AuthResult.unauthorized("User no longer exists");
        }
        return passToNext(context);
    }
}
