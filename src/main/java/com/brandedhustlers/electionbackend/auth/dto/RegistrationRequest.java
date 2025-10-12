package com.brandedhustlers.electionbackend.auth.dto;

public record RegistrationRequest(
        String firstName,
        String lastName,
        String email,
        String organizationName,
        String password
) {
}
