package com.team7.eventticketing.user.dto;

public record RegisterRequestDTO(
        String name,
        String email,
        String password,
        String phone
) {}