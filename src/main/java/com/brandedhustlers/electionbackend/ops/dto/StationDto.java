package com.brandedhustlers.electionbackend.ops.dto;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;

import java.time.Instant;
import java.util.UUID;

public record StationDto(
        UUID id,
        UUID orgId,
        UUID launchId,
        String name,
        String location,
        Integer capacity,
        Instant createdAt,
        DeleteYn deleteYn
) {
}
