package com.brandedhustlers.electionbackend.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record UpsertStation(
                @NotNull(message = "Launch ID is required") UUID launchId,
                String launchName,
                @NotBlank(message = "Location is required") String location,
                @Positive(message = "Capacity must be positive") Integer capacity) {
}
