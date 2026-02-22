package com.brandedhustlers.electionbackend.voter.dto;

import java.util.UUID;

public record ImportStatusDTO(
        UUID jobId,
        UUID orgId,
        String status,
        int total,
        int processed,
        int failed
) {
}
