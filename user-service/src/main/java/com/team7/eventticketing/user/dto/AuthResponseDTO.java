package com.team7.eventticketing.user.dto;

public record AuthResponseDTO(String token, long expiresIn) {}