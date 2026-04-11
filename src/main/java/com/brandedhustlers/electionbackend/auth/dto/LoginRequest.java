package com.brandedhustlers.electionbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
                String username,
                @Email String email,
                @NotBlank(message = "Password is mandatory") String password) {
}
