package com.brandedhustlers.electionbackend.auth.dto;

public record CreateOrganizationRequest(
        String organizationName,
        String organizationDescription
) {
}
