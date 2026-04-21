package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public record ElectionDTO(
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
                OrganizationDTO organization,
                DeleteYn deleteYn,
                boolean isPubliclyVisible,
                List<CandidateDTO> candidates,
                EligibilityPolicy eligibilityPolicy) {
}
