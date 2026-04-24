package com.brandedhustlers.electionbackend.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddUserToOrg(
        @NotNull(message = "User ID is required") UUID userId,
        @NotNull(message = "Organization ID is required") UUID orgId
) {
}
