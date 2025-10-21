package com.brandedhustlers.electionbackend.ops.dto;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;

import java.time.Instant;
import java.util.UUID;

public record LaunchDto(
        UUID id,
        UUID orgId,
        String name,
        String description,
        LaunchStatus status,
        Instant startAt,
        Instant endAt,
        Instant createdAt,
        DeleteYn deleteYn
) {
}
