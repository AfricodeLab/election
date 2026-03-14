package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for updating an existing election preset.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateElectionPresetRequest(
        @Size(max = 50) String name,
        @Size(max = 255) String description,
        UUID typeId,
        EligibilityPolicy defaultEligibilityPolicy,
        Long defaultExpectedVoterCount,
        Boolean defaultShouldEndAutomatically) {
}
