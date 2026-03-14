package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssignVotersCmd(
                @NotNull(message = "Election ID is required") UUID electionId,
                @NotEmpty(message = "Voter IDs list cannot be empty") List<UUID> voterIds) {
}
