package com.brandedhustlers.electionbackend.voter.dto;

public record OtpConfirmResponse(
        boolean verified,
        String message,
        String voterToken
) {
}
