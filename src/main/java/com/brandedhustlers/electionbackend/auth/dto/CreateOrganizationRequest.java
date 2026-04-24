package com.brandedhustlers.electionbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(max = 100, message = "Organization name must not exceed 100 characters")
        String organizationName,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String organizationDescription
) {
}
