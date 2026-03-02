package com.brandedhustlers.electionbackend.election.dto;

import java.util.UUID;

public record ElectionTypeOptionDto(
        UUID typeId,
        String electionTypeName

) {
}
