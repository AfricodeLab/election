package com.brandedhustlers.electionbackend.election.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a custom election type.
 */
public record CreateElectionTypeRequest(
                @NotBlank(message = "Name is required") @Size(max = 50) String name,
                @Size(max = 255) String description) {
}
