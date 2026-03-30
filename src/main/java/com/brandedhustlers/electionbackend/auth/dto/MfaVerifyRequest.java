package com.brandedhustlers.electionbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @NotBlank(message = "MFA token is required")
        String mfaToken,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
        @Pattern(regexp = "\\d{6}", message = "OTP must be 6 numeric digits")
        String otp
) {
}
