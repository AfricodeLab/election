package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.enums.ElectionType;

import java.sql.Timestamp;
import java.util.List;

public record CreateElectionRequest(
        String electionTitle,
        String electionDescription,
        Timestamp startTime,
        Timestamp endtime,
        List<CandidateDTO> candidates,
        ElectionType electionType,
        Long expectedVoterCount,
        boolean isPubliclyVisible,
        boolean shouldEndAutomatically
) {
}
