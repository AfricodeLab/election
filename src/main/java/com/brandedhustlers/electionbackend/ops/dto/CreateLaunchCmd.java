package com.brandedhustlers.electionbackend.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateLaunchCmd(
                @NotBlank(message = "Launch name is required") String name,
                String description,
                @NotNull(message = "Start time is required") Instant startAt,
                @NotNull(message = "End time is required") Instant endAt) {
}
