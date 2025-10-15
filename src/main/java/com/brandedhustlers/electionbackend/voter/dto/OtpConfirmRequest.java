package com.brandedhustlers.electionbackend.voter.dto;

import java.util.UUID;

public record OtpConfirmRequest(
        UUID electionId,
        String phoneOrVoterId,
        String token
) {
}
