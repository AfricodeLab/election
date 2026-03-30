package com.brandedhustlers.electionbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
                @NotBlank(message = "First name is mandatory") String firstName,
                @NotBlank(message = "Last name is mandatory") String lastName,
                String username,
                @NotBlank(message = "Default password is mandatory")
                @Size(min = 8, message = "Password must be at least 8 characters")
                @Pattern(
                        regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                        message = "Password must contain at least one uppercase letter and one digit"
                ) String defaultPassword,
                @NotBlank(message = "Company Email is mandatory") @Email(message = "Must be a valid email") String companyEmail,
                @NotBlank(message = "Role is mandatory") String userRole) {
}
