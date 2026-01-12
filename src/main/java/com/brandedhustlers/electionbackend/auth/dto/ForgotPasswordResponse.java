package com.brandedhustlers.electionbackend.auth.dto;

public record ForgotPasswordResponse(
        boolean success, String message, String otp
) {
}
