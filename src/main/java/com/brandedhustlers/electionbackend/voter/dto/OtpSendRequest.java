package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/verification/send}.
 *
 * @param email      Voter's registered email — used as the primary lookup key.
 * @param nationalId Voter's national ID — must match the record on file to
 *                   confirm identity before an OTP is dispatched.
 * @param electionId The election this verification is for. OTPs are scoped
 *                   per-election so one code cannot be reused across elections.
 * @param channel    Delivery channel: {@code EMAIL} (default) or {@code SMS}.
 */
public record OtpSendRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email")
        String email,

        @NotBlank(message = "National ID is required")
        String nationalId,

        @NotNull(message = "Election ID is required")
        UUID electionId,

        String channel) {

    /** Normalised channel — always uppercase, defaults to EMAIL. */
    public String resolvedChannel() {
        if (channel == null || channel.isBlank()) return "EMAIL";
        return channel.trim().toUpperCase();
    }
}
