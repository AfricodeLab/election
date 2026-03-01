package com.brandedhustlers.electionbackend.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken) {
}
