package com.brandedhustlers.electionbackend.audit.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditSearchFilter {
    private UUID orgId; // required
    private String eventType; // optional exact or prefix (UI can constrain)
    private String action; // not stored separately; UI maps to eventType sets or actionLabel prefixes if needed
    private UUID actorId; // optional
    private String targetType; // optional
    private UUID targetId; // optional
    private Instant from;
    private Instant to; // optional
    private String severity; // optional
    private int page = 0;
    private int size = 50;
    private String sort = "occurred_at,DESC";
}
