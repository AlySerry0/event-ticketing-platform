package com.team7.eventticketing.booking.security;

import com.team7.eventticketing.booking.repository.BookingRepository;
import com.team7.eventticketing.booking.security.AuthContext;
import com.team7.eventticketing.booking.security.AuthHandler;
import com.team7.eventticketing.booking.security.AuthResult;

public class UserLoaderHandler extends AuthHandler {

    private final BookingRepository bookingRepository;

    public UserLoaderHandler(BookingRepository repo) {
        this.bookingRepository = repo;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        if (!bookingRepository.userExistsByEmail(context.getAuthenticatedEmail())) {
            return AuthResult.unauthorized("User no longer exists");
        }
        return passToNext(context);
    }
}
