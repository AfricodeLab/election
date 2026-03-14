package com.brandedhustlers.electionbackend.auth.dto;

public record LoginResponse(
                boolean success,
                String accessToken,
                String refreshToken,
                String username,
                String userEmail
                ) {
}
