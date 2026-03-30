package com.brandedhustlers.electionbackend.auth.dto;

public record MfaVerifyResponse(
        boolean success,
        String accessToken,
        String refreshToken,
        String username,
        String userEmail
) {
}
