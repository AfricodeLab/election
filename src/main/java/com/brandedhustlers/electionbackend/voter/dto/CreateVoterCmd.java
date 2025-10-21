package com.brandedhustlers.electionbackend.voter.dto;

public record CreateVoterCmd(
        String name,
        String phone,
        String email,
        String nationalId
) {
}
