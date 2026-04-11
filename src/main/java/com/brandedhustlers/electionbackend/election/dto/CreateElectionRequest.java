package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public record CreateElectionRequest(
        @NotBlank(message = "Election title is required") @Size(max = 100) String electionTitle,
        @Size(max = 500) String electionDescription,
        @NotNull(message = "Start time is required") Timestamp startTime,
        @NotNull(message = "End time is required") Timestamp endtime,
        @Valid List<CandidateDTO> candidates,

        /**
         * UUID of the ElectionTypeEntity (custom or system type).
         */
        @NotBlank(message = "Election type is required") String typeId,

        /**
         * Optional: UUID of an ElectionPreset to load defaults from.
         * If provided, eligibilityPolicy / expectedVoterCount may be pre-filled
         * unless explicitly overridden in this request.
         */
        UUID presetId,

        Long expectedVoterCount,
        boolean isPubliclyVisible,
        boolean shouldEndAutomatically,
        EligibilityPolicy electionEligibilityPolicy) {
}
