package com.brandedhustlers.electionbackend.ops.dto;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;

import java.time.Instant;
import java.util.UUID;

public record DeviceDto(
        UUID id,
        UUID stationId,
        String label,
        DeviceStatus status,
        Instant lastSeen,
        Long lastLatencyMs,
        String appVersion,
        Instant createdAt,
        DeleteYn deleteYn
) {
}
