package com.brandedhustlers.electionbackend.voting.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CastVoteCmd(
                @NotNull(message = "Election ID is required") UUID electionId,
                @NotNull(message = "Candidate ID is required") UUID candidateId,
                String voterToken,
                UUID deviceId,
                String idempotencyKey) {
}
