package com.brandedhustlers.electionbackend.audit.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEnvelope {
    private UUID eventId;
    private String eventType;
    private Instant occurredAt;
    private UUID orgId;

    private String actionLabel; // human-facing label

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Actor {
        public UUID actorId;
        public String actorName;
        public String actorRole;
        public String actorType;
    }

    private Actor actor;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Target {
        public String aggregateType;
        public UUID aggregateId;
        public String aggregateLabel;
    }

    private Target target;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Context {
        public String source;
        public String correlationId;
        public String ip;
        public String ua;
    }

    private Context context;

    private String severity; // INFO|WARN|ERROR|SECURITY
    private Integer version; // envelope version
}