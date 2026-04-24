package com.brandedhustlers.electionbackend.election.mapper;

import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ElectionMapper {

    public ElectionDTO entityToDto(Election election) {
        if (election == null)
            return null;

        ElectionTypeDTO typeDTO = null;
        if (election.getElectionType() != null) {
            var t = election.getElectionType();
            typeDTO = new ElectionTypeDTO(
                    t.getTypeId(),
                    t.getName(),
                    t.getDescription(),
                    t.isSystem(),
                    t.getOrganization() != null ? t.getOrganization().getOrganizationId() : null,
                    t.getCreatedAt());
        }

        List<CandidateDTO> candidateDTOs = election.getCandidates() == null
                ? List.of()
                : election.getCandidates().stream()
                        .map(this::candidateToDto)
                        .collect(Collectors.toList());

        return new ElectionDTO(
                election.getElectionId(),
                election.getElectionTitle(),
                election.getElectionDescription(),
                typeDTO,
                election.getElectionStatus(),
                election.getElectionStartTime(),
                election.getElectionEndTime(),
                election.getTotalVoters(),
                election.getTotalVotes(),
                election.getCreatedAt(),
                new OrganizationDTO(
                        election.getOrganization().getOrganizationId(),
                        election.getOrganization().getOrganizationName(),
                        election.getOrganization().getOrganizationDescription(),
                        election.getOrganization().isEnabled(),   // was incorrectly calling getCreatedBy().isMfaEnabled()
                        election.getOrganization().getCreatedAt(),
                        election.getOrganization().getUpdatedAt(),
                        election.getOrganization().getDeleteYn()
                ),
                election.getDeleteYn(),
                election.isPubliclyVisible(),
                candidateDTOs,
                election.getEligibilityPolicy());
    }

    private CandidateDTO candidateToDto(Candidate c) {
        return new CandidateDTO(
                c.getCandidateId(),
                c.getCandidateName(),
                c.getAffiliation(),
                c.getManifesto(),
                c.getCandidatePhotoURL(),
                c.getDateOfBirth(),
                c.getPlaceOfBirth(),
                c.isIncumbent(),
                c.getDeleteYn(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    /**
     * Not used for persistence (elections are created via ElectionService),
     * retained for interface compatibility.
     */
    public Election dtoToEntity(ElectionDTO electionDTO) {
        throw new UnsupportedOperationException(
                "Use ElectionService.createElection() to create elections.");
    }
}
