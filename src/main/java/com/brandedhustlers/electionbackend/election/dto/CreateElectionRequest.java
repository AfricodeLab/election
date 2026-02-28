package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public record CreateElectionRequest(
                String electionTitle,
                String electionDescription,
                Timestamp startTime,
                Timestamp endtime,
                List<CandidateDTO> candidates,

                /**
                 * UUID of the ElectionTypeEntity (custom or system type).
                 */
                String typeId,

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
