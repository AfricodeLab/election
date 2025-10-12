package com.brandedhustlers.electionbackend.auth.dto;

public record CreateUserResponse(
        boolean success,
        String userRole
) {
}
