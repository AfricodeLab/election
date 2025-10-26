package com.brandedhustlers.electionbackend.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * DomainEvent — canonical, audit row (dashboard friendly).
 * We store snapshots of actor/target labels to avoid runtime joins and to preserve the historical label used at the time.
 */
@Entity
@Table(name = "domain_event", schema = "audit",
        indexes = {
                @Index(name = "idx_audit_org_time", columnList = "org_id, occurred_at DESC"),
                @Index(name = "idx_audit_org_evt_type_time", columnList = "org_id, event_type, occurred_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId; // idempotency key

    @Column(name = "org_id", nullable = false)
    private UUID orgId; // org scoping

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt; // when it happened (producer clock)

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt; // when we stored it

    @Column(name = "event_type", nullable = false)
    private String eventType; // machine type e.g. election.candidate_updated

    @Column(name = "action_label", nullable = false)
    private String actionLabel; // human label e.g. "Candidate list update"

    // Actor snapshot
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName; // snapshot

    @Column(name = "actor_role")
    private String actorRole; // snapshot (Super Admin, Org Admin, ...)

    @Column(name = "actor_type")
    private String actorType; // USER|SYSTEM|DEVICE

    // Target snapshot
    @Column(name = "target_type")
    private String targetType; // Election|Candidate|Launch|User|Voter|Vote|Preset|Message|Batch

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "target_label")
    private String targetLabel; // snapshot e.g. "National 2025"

    // Context
    @Column(name = "severity", nullable = false)
    private String severity; // INFO|WARN|ERROR|SECURITY

    @Column(name = "source")
    private String source; // WEB|MOBILE|WORKER

    @Column(name = "correlation_id")
    private String correlationId; // to link multi-step flows

    @Column(name = "ip_hash")
    private String ipHash; // never store raw IP

    @Column(name = "ua_hash")
    private String uaHash; // never store raw UA
}