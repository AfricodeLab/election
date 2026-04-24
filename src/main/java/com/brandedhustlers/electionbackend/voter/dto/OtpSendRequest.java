package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/verification/send}.
 *
 * @param email      Voter's registered email — used as the primary lookup key.
 * @param nationalId Voter's national ID — must match the record on file to
 *                   confirm identity before an OTP is dispatched.
 * @param electionId Optional. When provided the OTP is scoped to that election
 *                   (legacy/deep-link flow). When null the OTP is a general
 *                   identity proof — the voter will see all eligible elections
 *                   after confirming their code.
 * @param channel    Delivery channel: {@code EMAIL} (default) or {@code SMS}.
 */
public record OtpSendRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email")
        String email,

        @NotBlank(message = "National ID is required")
        String nationalId,

        UUID electionId,   // nullable — null = identity-only (no election scope)

        String channel) {

    /** Normalised channel — always uppercase, defaults to EMAIL. */
    public String resolvedChannel() {
        if (channel == null || channel.isBlank()) return "EMAIL";
        return channel.trim().toUpperCase();
    }
}
