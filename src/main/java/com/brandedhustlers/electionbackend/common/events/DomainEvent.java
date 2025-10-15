package com.brandedhustlers.electionbackend.common.events;

import java.sql.Timestamp;
import java.util.UUID;

public record DomainEvent(
        UUID id,
        String type,
        String aggregateType,
        UUID aggregateId,
        UUID orgId,
        UUID userId,
        String payload,
        Timestamp createdAt
) {
}
