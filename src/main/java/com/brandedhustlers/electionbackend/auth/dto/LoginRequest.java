package com.brandedhustlers.electionbackend.auth.dto;

public record LoginRequest(
        String username,
        String email,
        String password
) {
}
