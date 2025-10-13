package com.brandedhustlers.electionbackend.auth.dto;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;

import java.sql.Timestamp;
import java.util.UUID;

public record OrganizationDTO(
        UUID organizationId,
        String organizationName,
        String organizationDescription,
        boolean isEnabled,
        Timestamp createdAt,
        Timestamp updatedAt,
        DeleteYn deleteYn
) {
}
