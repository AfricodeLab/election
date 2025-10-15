package com.brandedhustlers.electionbackend.voter.dto;

import java.util.UUID;

public record OtpSendRequest(
        UUID electionId,
        String phoneOrVoterId
) {
}
