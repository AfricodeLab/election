package com.brandedhustlers.electionbackend.ops.dto;

import jakarta.validation.constraints.Positive;

public record OpsPolicy(
                @Positive(message = "Offline threshold must be positive") long offlineThresholdMs,
                @Positive(message = "Degraded latency must be positive") long degradedLatencyMs,
                @Positive(message = "Recovery beats must be positive") int onlineRecoveryBeats) {
}
