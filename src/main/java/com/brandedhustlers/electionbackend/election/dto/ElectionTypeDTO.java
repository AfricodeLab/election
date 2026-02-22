package com.brandedhustlers.electionbackend.election.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for ElectionTypeEntity.
 */
public record ElectionTypeDTO(
        UUID typeId,
        String name,
        String description,
        boolean isSystem,
        UUID organizationId,
        Instant createdAt) {
}
