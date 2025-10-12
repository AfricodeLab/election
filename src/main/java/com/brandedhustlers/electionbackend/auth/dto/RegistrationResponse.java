package com.brandedhustlers.electionbackend.auth.dto;

public record RegistrationResponse(
        boolean success,
        String userType
) {
}
