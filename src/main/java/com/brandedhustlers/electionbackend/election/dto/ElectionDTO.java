package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.enums.ElectionType;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public record ElectionDTO(
        UUID electionId,
        String electionTitle,
        String electionDescription,
        ElectionType electionType,
        ElectionStatus electionStatus,
        Timestamp electionStartTime,
        Timestamp electionEndTime,
        Long totalVoters,
        Long totalVotes,
        Timestamp createdAt,
        Organization organization,
        DeleteYn deleteYn,
        boolean isPubliclyVisible,
        List<CandidateDTO> candidates,
        EligibilityPolicy eligibilityPolicy
) {
}