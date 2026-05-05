package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.*;
import com.team7.eventticketing.user.model.User;
import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.model.UserStatus;
import com.team7.eventticketing.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponseDTO register(RegisterRequestDTO req) {
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
        user.setPassword(passwordEncoder.encode(req.password())); // BCrypt hash
        user.setPhone(req.phone());
        user.setRole(UserRole.ATTENDEE);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());

//        user = userRepository.save(user);
        try {
            user = userRepository.save(user);
        } catch (Exception e) {
            // Sequence out of sync — reset it and retry
            userRepository.resetSequence();
            user = userRepository.save(user);
        }

        // TODO: fire Observer → MongoDB REGISTERED event here (M2 Observer pattern)

        String token = jwtService.generateToken(
                user.getEmail(), user.getId(), user.getRole().name());

        return new AuthResponseDTO(token, jwtService.getExpirationMs());
    }


    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}