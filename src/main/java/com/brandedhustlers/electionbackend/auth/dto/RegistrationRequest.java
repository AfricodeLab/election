package com.brandedhustlers.electionbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
                @NotBlank(message = "First name is mandatory") String firstName,
                @NotBlank(message = "Last name is mandatory") String lastName,
                @NotBlank(message = "Email is mandatory") @Email(message = "Email should be valid") String email,
                @NotBlank(message = "Username is mandatory") String username,
                @NotBlank(message = "Organization name is mandatory") String organizationName,
                @NotBlank(message = "Password is mandatory") @Size(min = 6, message = "Password must be at least 6 characters") String password,
                CreateOrganizationRequest userOrg) {
}
