package com.brandedhustlers.electionbackend.voting.dto;

import java.util.UUID;

public record CastVoteCmd(
        UUID electionId,
        UUID candidateId,
        String voterToken,
        UUID deviceId,
        String idempotencyKey) {

}
