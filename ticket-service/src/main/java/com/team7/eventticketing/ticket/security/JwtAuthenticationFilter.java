package com.team7.eventticketing.ticket.security;

import com.team7.eventticketing.ticket.repository.TicketRepository;
import com.team7.eventticketing.ticket.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TicketRepository ticketRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TicketRepository ticketRepository) {
        this.jwtService = jwtService;
        this.ticketRepository = ticketRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Build the GoF handler chain
        AuthContext ctx = new AuthContext(request);

        TokenExtractionHandler extraction = new TokenExtractionHandler();
        SignatureValidationHandler validation = new SignatureValidationHandler(jwtService);
        UserLoaderHandler userLoader = new UserLoaderHandler(ticketRepository);
        RoleAuthorizationHandler roleCheck = new RoleAuthorizationHandler();

        extraction.setNext(validation).setNext(userLoader).setNext(roleCheck);

        // Run the chain
        AuthResult result = extraction.handle(ctx);

        if (!result.isSuccess()) {
            response.setStatus(result.getStatusCode());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"" + result.getMessage() + "\"}"
            );
            return; // Do NOT call filterChain.doFilter — short-circuit here
        }

        // All handlers passed — populate Spring Security context
        var auth = new UsernamePasswordAuthenticationToken(
                ctx.getAuthenticatedEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getAuthenticatedRole()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}