package com.brandedhustlers.electionbackend.results.dto;

import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;

import java.sql.Timestamp;
import java.util.UUID;

public record TallyDto(
        UUID electionId,
        String electionTitle,
        CandidateDTO candidate,
        long voteCount,
        Timestamp lastUpdated
) {
}
