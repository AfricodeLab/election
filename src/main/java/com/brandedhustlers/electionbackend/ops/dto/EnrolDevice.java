package com.brandedhustlers.electionbackend.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrolDevice(
                @NotNull(message = "Station ID is required") UUID stationId,
                @NotBlank(message = "Label is required") String label,
                String appVersion) {
}
