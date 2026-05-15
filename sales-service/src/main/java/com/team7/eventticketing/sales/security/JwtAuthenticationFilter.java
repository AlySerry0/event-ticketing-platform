package com.team7.eventticketing.sales.security;

import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import com.team7.eventticketing.sales.service.JwtService;
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
    private final TicketSaleRepository ticketSaleRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TicketSaleRepository ticketSaleRepository) {
        this.jwtService = jwtService;
        this.ticketSaleRepository = ticketSaleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null)
            correlationId = java.util.UUID.randomUUID().toString();
        org.slf4j.MDC.put("correlationId", correlationId);

        try{
            // Build the GoF handler chain
            AuthContext ctx = new AuthContext(request);

            GatewayHeaderHandler gatewayCheck = new GatewayHeaderHandler();
            TokenExtractionHandler extraction = new TokenExtractionHandler();
            SignatureValidationHandler validation = new SignatureValidationHandler(jwtService);
            UserLoaderHandler userLoader = new UserLoaderHandler(ticketSaleRepository);
            RoleAuthorizationHandler roleCheck = new RoleAuthorizationHandler();

            gatewayCheck.setNext(extraction).setNext(validation).setNext(userLoader).setNext(roleCheck);

            // Run the chain
            AuthResult result = gatewayCheck.handle(ctx);

            if (!result.isSuccess()) {
                response.setStatus(result.getStatusCode());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"" + result.getMessage() + "\"}"
                );
                return; // Do NOT call filterChain.doFilter — short-circuit here
            }

            // All handlers passed — populate Spring Security context
            org.slf4j.MDC.put("jwtToken", ctx.getToken());

            var auth = new UsernamePasswordAuthenticationToken(
                    ctx.getAuthenticatedEmail(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getAuthenticatedRole()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove("correlationId"); // Essential for thread safety [cite: 1582]
            org.slf4j.MDC.remove("jwtToken");
        }
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // List all your public endpoints here. The filter will completely skip them.
        return path.equals("/api/sales/health") || path.equals("/api/seed");
    }
}