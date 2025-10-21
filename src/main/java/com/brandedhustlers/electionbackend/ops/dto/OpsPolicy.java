package com.brandedhustlers.electionbackend.ops.dto;

public record OpsPolicy(long offlineThresholdMs,
                        long degradedLatencyMs,
                        int onlineRecoveryBeats) {}

