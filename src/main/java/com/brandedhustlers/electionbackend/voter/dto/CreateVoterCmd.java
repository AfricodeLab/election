package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Command to create a voter.
 *
 * @param name       Voter's full name
 * @param phone      Phone number
 * @param email      Email address
 * @param nationalId National ID or equivalent
 * @param attributes Optional eligibility attributes (age, region, citizen,
 *                   etc.)
 */
public record CreateVoterCmd(
        @NotBlank(message = "Name is required") String name,
        String phone,
        @NotBlank(message = "Email is required") @Email(message = "Must be a valid email") String email,
        String nationalId,
        Map<String, Object> attributes) {
    public CreateVoterCmd(String name, String phone, String email, String nationalId) {
        this(name, phone, email, nationalId, null);
    }
}
