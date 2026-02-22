package com.brandedhustlers.electionbackend.election.dto;

/**
 * Request DTO for creating a custom election type.
 */
public record CreateElectionTypeRequest(
        String name,
        String description) {
}
