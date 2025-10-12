package com.brandedhustlers.electionbackend.auth.dto;

public record LoginResponse(
        boolean success,
        String accessToken
) {
}
