package com.brandedhustlers.electionbackend.auth.dto;

public record TokenRefreshRequest(
        String refreshToken) {
}
