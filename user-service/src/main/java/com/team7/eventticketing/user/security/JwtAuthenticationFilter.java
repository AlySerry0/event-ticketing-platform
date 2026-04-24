package com.team7.eventticketing.user.security;

import com.team7.eventticketing.user.repository.UserRepository;
import com.team7.eventticketing.user.service.JwtService;
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
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    private static final Set<String> BOTH  = Set.of("ATTENDEE", "ADMIN");
    private static final Set<String> ADMIN = Set.of("ADMIN");

    // Order matters: more specific patterns first.
    private static final List<EndpointRule> ENDPOINT_RULES = List.of(
            // --- CC-2: ADMIN-only role management (must be before generic PUT /{id}) ---
            new EndpointRule("PUT",    "^/api/users/\\d+/role$",                       ADMIN),

            // --- UserController: collection & static paths ---
            new EndpointRule("POST",   "^/api/users$",                                  BOTH),
            new EndpointRule("GET",    "^/api/users$",                                  BOTH),
            new EndpointRule("GET",    "^/api/users/search$",                           BOTH),
            new EndpointRule("GET",    "^/api/users/reports/top-attendees$",            BOTH),
            new EndpointRule("GET",    "^/api/users/preferences/search$",               BOTH),
            new EndpointRule("GET",    "^/api/users/preferences/category$",             BOTH),
            new EndpointRule("GET",    "^/api/users/email/[^/]+$",                      BOTH),
            new EndpointRule("GET",    "^/api/users/phone/[^/]+$",                      BOTH),

            // --- UserController: /{id} sub-paths (specific before generic) ---
            new EndpointRule("PUT",    "^/api/users/\\d+/deactivate$",                  BOTH),
            new EndpointRule("PATCH",  "^/api/users/\\d+/activate$",                    BOTH),
            new EndpointRule("PUT",    "^/api/users/\\d+/preferences$",                 BOTH),
            new EndpointRule("GET",    "^/api/users/\\d+/booking-summary$",             BOTH),
            new EndpointRule("GET",    "^/api/users/\\d+/profile$",                     BOTH),

            // --- FavoriteVenueController (nested under /api/users/{userId}/venues) ---
            new EndpointRule("PUT",    "^/api/users/\\d+/venues/\\d+/default$",         BOTH),
            new EndpointRule("GET",    "^/api/users/\\d+/venues/default$",              BOTH),
            new EndpointRule("POST",   "^/api/users/\\d+/venues$",                      BOTH),
            new EndpointRule("GET",    "^/api/users/\\d+/venues$",                      BOTH),
            new EndpointRule("GET",    "^/api/users/\\d+/venues/\\d+$",                 BOTH),
            new EndpointRule("PUT",    "^/api/users/\\d+/venues/\\d+$",                 BOTH),
            new EndpointRule("DELETE", "^/api/users/\\d+/venues/\\d+$",                 BOTH),

            // --- UserController: generic /{id} (must be LAST to avoid shadowing) ---
            new EndpointRule("GET",    "^/api/users/\\d+$",                             BOTH),
            new EndpointRule("PUT",    "^/api/users/\\d+$",                             BOTH),
            new EndpointRule("DELETE", "^/api/users/\\d+$",                             BOTH)
    );

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        AuthContext ctx = new AuthContext(request);
        ctx.setAllowedRoles(resolveAllowedRoles(request.getMethod(), request.getRequestURI()));

        TokenExtractionHandler    extraction = new TokenExtractionHandler();
        SignatureValidationHandler validation = new SignatureValidationHandler(jwtService);
        UserLoaderHandler         userLoader = new UserLoaderHandler(userRepository);
        RoleAuthorizationHandler  roleCheck  = new RoleAuthorizationHandler();

        extraction.setNext(validation).setNext(userLoader).setNext(roleCheck);

        AuthResult result = extraction.handle(ctx);

        if (!result.isSuccess()) {
            response.setStatus(result.getStatusCode());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"" + result.getMessage() + "\"}"
            );
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                ctx.getAuthenticatedEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getAuthenticatedRole()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private Set<String> resolveAllowedRoles(String method, String path) {
        for (EndpointRule rule : ENDPOINT_RULES) {
            if (rule.method.equalsIgnoreCase(method) && rule.pattern.matcher(path).matches()) {
                return rule.allowedRoles;
            }
        }
        return Set.of(); // no match → RoleAuthorizationHandler returns 403
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.equals("/api/users/health") || path.equals("/api/seed");
    }

    private static final class EndpointRule {
        final String method;
        final Pattern pattern;
        final Set<String> allowedRoles;

        EndpointRule(String method, String regex, Set<String> allowedRoles) {
            this.method = method;
            this.pattern = Pattern.compile(regex);
            this.allowedRoles = allowedRoles;
        }
    }
}