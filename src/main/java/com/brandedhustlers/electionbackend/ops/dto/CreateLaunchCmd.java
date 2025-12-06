package com.brandedhustlers.electionbackend.ops.dto;

import java.time.Instant;

public record CreateLaunchCmd(
        String name,
        String description,
        Instant startAt,
        Instant endAt
) {
}
