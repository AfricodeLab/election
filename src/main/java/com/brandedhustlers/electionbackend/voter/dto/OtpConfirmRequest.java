package com.brandedhustlers.electionbackend.voter.dto;

public record OtpConfirmRequest(
        String email,
        String otp
) {
}
