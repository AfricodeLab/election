package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;

import java.util.UUID;

/**
 * Request DTO for creating a new election preset.
 */
public record CreateElectionPresetRequest(
        String name,
        String description,

        /**
         * The UUID of the ElectionTypeEntity to link to this preset.
         */
        UUID typeId,

        /**
         * Default eligibility policy applied when creating elections from this preset.
         */
        EligibilityPolicy defaultEligibilityPolicy,

        Long defaultExpectedVoterCount,

        boolean defaultShouldEndAutomatically) {
}
