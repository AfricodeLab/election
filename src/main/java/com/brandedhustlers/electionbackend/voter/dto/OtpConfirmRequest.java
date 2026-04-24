package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/verification/confirm}.
 * Used for both self-service OTP and in-person station-PIN flows.
 *
 * <p>{@code electionId} is optional. Null means a general identity-proof OTP
 * (voter will see all eligible elections after confirming). Non-null means
 * the OTP was scoped to a specific election (legacy/deep-link flow).</p>
 */
public record OtpConfirmRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email")
        String email,

        @NotBlank(message = "Code is required")
        String otp,

        UUID electionId) {  // nullable — null = identity-only flow
}
