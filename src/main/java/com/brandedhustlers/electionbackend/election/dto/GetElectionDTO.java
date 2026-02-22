package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public record GetElectionDTO(
                UUID electionId,
                String electionTitle,
                String electionDescription,
                ElectionTypeDTO electionType,
                ElectionStatus electionStatus,
                Timestamp electionStartTime,
                Timestamp electionEndTime,
                Long totalVoters,
                Long totalVotes,
                Timestamp createdAt,
                Organization organization,
                DeleteYn deleteYn,
                boolean isPubliclyVisible,
                List<CandidateDTO> electionCandidates) {
}
