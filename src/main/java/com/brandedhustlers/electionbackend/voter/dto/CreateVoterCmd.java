package com.brandedhustlers.electionbackend.voter.dto;

import java.util.Map;

/**
 * Command to create a voter.
 *
 * @param name Voter's full name
 * @param phone Phone number
 * @param email Email address
 * @param nationalId National ID or equivalent
 * @param attributes Optional eligibility attributes (age, region, citizen, etc.)
 */
public record CreateVoterCmd(
        String name,
        String phone,
        String email,
        String nationalId,
        Map<String, Object> attributes  // NEW - optional
) {
    public CreateVoterCmd(String name, String phone, String email, String nationalId) {
        this(name, phone, email, nationalId, null);
    }
}