package com.brandedhustlers.electionbackend.voter.dto;

import jakarta.validation.constraints.Email;

/**
 * Patch command to update a voter's contact info.
 * Both fields are optional — only non-null, non-blank values are applied.
 */
public record UpdateVoterContactCmd(
        @Email(message = "Must be a valid email") String email,
        String phone) {
}
