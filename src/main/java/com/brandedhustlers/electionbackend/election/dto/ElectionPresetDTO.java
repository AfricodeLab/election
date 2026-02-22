package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for ElectionPreset.
 */
public record ElectionPresetDTO(
        UUID presetId,
        String name,
        String description,
        ElectionTypeDTO electionType,
        UUID organizationId,
        EligibilityPolicy defaultEligibilityPolicy,
        Long defaultExpectedVoterCount,
        boolean defaultShouldEndAutomatically,
        Instant createdAt) {
}
