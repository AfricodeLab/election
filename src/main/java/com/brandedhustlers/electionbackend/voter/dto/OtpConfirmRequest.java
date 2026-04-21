package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/verification/confirm}.
 * Used for both self-service OTP and in-person station-PIN flows.
 */
public record OtpConfirmRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email")
        String email,

        @NotBlank(message = "Code is required")
        String otp,

        @NotNull(message = "Election ID is required")
        UUID electionId) {
}
