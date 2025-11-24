package com.brandedhustlers.electionbackend.election.dto;

public record UpdateCandidateCmd(
        String candidateName,
        Integer candidatePosition
) {
}
