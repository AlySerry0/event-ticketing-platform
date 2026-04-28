package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.*;
import com.team7.eventticketing.user.model.User;
import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.model.UserStatus;
import com.team7.eventticketing.user.observer.EntityObserver;
import com.team7.eventticketing.user.observer.MongoEventLogger;
import com.team7.eventticketing.user.repository.AuthEventRepository;
import com.team7.eventticketing.user.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    // -----------------------------------------------------------------------
    // Classical GoF Observer — subject side
    // Each service owns its own observer list (Section 3.3)
    // -----------------------------------------------------------------------
    private final List<EntityObserver> observers = new ArrayList<>();

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       BCryptPasswordEncoder passwordEncoder,
                       AuthEventRepository authEventRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;

        // Register the MongoEventLogger observer at construction time.
        // MongoEventLogger is NOT a Spring bean — we instantiate it manually
        // as required by Section 3.3 (classical GoF, not Spring @EventListener).
        this.registerObserver(new MongoEventLogger(authEventRepository));
    }

    // -----------------------------------------------------------------------
    // Observer management methods (Section 3.3)
    // -----------------------------------------------------------------------

    public void registerObserver(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(EntityObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies all registered observers of a state change.
     * The first argument is the action string (what happened).
     * The second argument is the payload (relevant data).
     */
    private void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }

    // -----------------------------------------------------------------------
    // Auth methods
    // -----------------------------------------------------------------------

    public AuthResponseDTO register(@NonNull RegisterRequestDTO req) {
        // Validate required fields
        if (isBlank(req.name()) || isBlank(req.email())
                || isBlank(req.password()) || isBlank(req.phone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "All fields are required");
        }

        // Check uniqueness
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phone already registered");
        }

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setPhone(req.phone());
        user.setRole(UserRole.ATTENDEE);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());

        user = userRepository.save(user);

        // S1-P11 + S1-L1: Trigger Observer on user creation → logs REGISTERED to MongoDB
        notifyObservers("REGISTERED", Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "timestamp", LocalDateTime.now()
        ));

        String token = jwtService.generateToken(
                user.getEmail(), user.getId(), user.getRole().name());

        return new AuthResponseDTO(token, jwtService.getExpirationMs());
    }

    public AuthResponseDTO login(LoginRequestDTO req) {
        // Validate required fields
        if (isBlank(req.email()) || isBlank(req.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email and password are required");
        }

        // Find user by email
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // Verify password against BCrypt hash
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid credentials");
        }

        // Block deactivated accounts
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account is deactivated");
        }

        // S1-P12 + S1-L2: Trigger Observer on login → logs LOGGED_IN to MongoDB
        notifyObservers("LOGGED_IN", Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "timestamp", LocalDateTime.now()
        ));

        String token = jwtService.generateToken(
                user.getEmail(), user.getId(), user.getRole().name());

        return new AuthResponseDTO(token, jwtService.getExpirationMs());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}