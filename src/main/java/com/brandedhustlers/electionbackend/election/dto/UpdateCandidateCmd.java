package com.brandedhustlers.electionbackend.election.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateCandidateCmd(
        @NotBlank
        String candidateName,
        @Positive
        Integer candidatePosition
) {
}
