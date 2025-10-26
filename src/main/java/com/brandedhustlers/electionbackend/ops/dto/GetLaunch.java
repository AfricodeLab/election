package com.brandedhustlers.electionbackend.ops.dto;

import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GetLaunch(
        UUID id,
        UUID orgId,
        String name,
        String description,
        LaunchStatus status,
        Instant startAt,
        Instant endAt,
        Instant createdAt,
        List<ElectionDTO> associatedElections
) {
}
