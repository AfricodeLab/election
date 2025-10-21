package com.brandedhustlers.electionbackend.voting.dto;

import java.util.UUID;

public record VoteAckDTO(UUID voteId,
                         String status,
                         String reason) {
    
}

