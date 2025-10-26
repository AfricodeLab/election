package com.brandedhustlers.electionbackend.auth.dto;

public record CreateUserRequest(
        String firstName,
        String lastName,
        String username,
        String defaultPassword,
        String companyEmail,
        String userRole
) {
}
