package com.brandedhustlers.electionbackend.voter.dto;

import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;

import java.util.UUID;

public record VoterDTO(
        String name,
        String phone,
        String email,
        String nationalId,
        VoterStatus voterStatus,
        UUID voterId
) {
}
