package com.brandedhustlers.electionbackend.audit.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventDTO {
    private UUID auditId;
    private UUID eventId;
    private UUID orgId;
    private Instant occurredAt;
    private String eventType;
    private String actionLabel;
    private UUID actorId;
    private String actorName;
    private String actorRole;
    private String actorType;
    private String targetType;
    private UUID targetId;
    private String targetLabel;
    private String severity;
    private String source;
    private String correlationId;
}
