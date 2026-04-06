package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param email   Voter's registered email (always used as the lookup key).
 * @param channel Delivery channel for the OTP code: EMAIL (default) or SMS.
 *                If null or blank, defaults to EMAIL.
 */
public record OtpSendRequest(
        @NotBlank(message = "Email is required") @Email(message = "Must be a valid email") String email,
        String channel) {

    /** Normalised channel — always uppercase, defaults to EMAIL. */
    public String resolvedChannel() {
        if (channel == null || channel.isBlank()) return "EMAIL";
        return channel.trim().toUpperCase();
    }
}
