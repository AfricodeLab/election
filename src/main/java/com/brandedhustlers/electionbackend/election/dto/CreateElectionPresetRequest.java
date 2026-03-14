package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for creating a new election preset.
 */
public record CreateElectionPresetRequest(
                @NotBlank(message = "Name is required") @Size(max = 50) String name,
                @Size(max = 255) String description,

                /**
                 * The UUID of the ElectionTypeEntity to link to this preset.
                 */
                @NotNull(message = "Type ID is required") UUID typeId,

                /**
                 * Default eligibility policy applied when creating elections from this preset.
                 */
                EligibilityPolicy defaultEligibilityPolicy,

                Long defaultExpectedVoterCount,

                boolean defaultShouldEndAutomatically) {
}
