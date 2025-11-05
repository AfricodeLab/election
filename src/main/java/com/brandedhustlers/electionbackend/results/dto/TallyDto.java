package com.brandedhustlers.electionbackend.results.dto;

import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;

import java.sql.Timestamp;
import java.util.UUID;

public record TallyDto(
        UUID electionId,
        CandidateDTO candidate,
        String affiliation,
        String candidatePhotoURL,
        String manifesto,
        boolean isIncumbent,
        long voteCount,
        Timestamp lastUpdated
) {
}
