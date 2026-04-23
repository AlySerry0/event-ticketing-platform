package com.team7.eventticketing.user.controller;

import com.team7.eventticketing.user.dto.AuthResponseDTO;
import com.team7.eventticketing.user.dto.LoginRequestDTO;
import com.team7.eventticketing.user.dto.RegisterRequestDTO;
import com.team7.eventticketing.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }
}