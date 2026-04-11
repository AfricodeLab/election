package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpConfirmRequest(
                @NotBlank(message = "Email is required") @Email(message = "Must be a valid email") String email,
                @NotBlank(message = "OTP is required") String otp) {
}
