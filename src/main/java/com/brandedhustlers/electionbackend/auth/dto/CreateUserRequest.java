package com.brandedhustlers.electionbackend.auth.dto;

import com.brandedhustlers.electionbackend.auth.enums.UserRole;

public record CreateUserRequest(
        String firstName,
        String lastName,
        String username,
        String companyEmail,
        UserRole userRole
) {
}
