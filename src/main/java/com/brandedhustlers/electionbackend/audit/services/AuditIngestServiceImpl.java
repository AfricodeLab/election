package com.brandedhustlers.electionbackend.audit.services;

import com.brandedhustlers.electionbackend.audit.domain.DomainEvent;
import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import com.brandedhustlers.electionbackend.audit.repository.DomainEventRepository;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.FeatureFlagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * AuditIngestServiceImpl — validates, redacts, maps to DomainEvent, idempotent by eventId.
 * Justification: ingestion must never explode the pipeline; duplicates are tolerated (unique constraint).
 */
@Slf4j
@Service
public class AuditIngestServiceImpl implements AuditIngestService {
    private final DomainEventRepository repo;
    private final RedactionService redact;
    private final FeatureFlagService featureFlags;
    private final Clock clock;

    public AuditIngestServiceImpl(DomainEventRepository repo,
                           RedactionService redact,
                           FeatureFlagService featureFlags) {
        this.repo = repo;
        this.redact = redact;
        this.featureFlags = featureFlags;
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public void ingest(AuditEnvelope env) {
        // Validate requireds
        Objects.requireNonNull(env.getEventId(), "eventId is required");
        Objects.requireNonNull(env.getOccurredAt(), "occurredAt is required");
        Objects.requireNonNull(env.getOrgId(), "orgId is required");

        if (env.getEventType() == null || env.getEventType().isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (env.getActionLabel() == null || env.getActionLabel().isBlank()) {
            throw new IllegalArgumentException("actionLabel is required");
        }

        // Feature gate: silently skip if AUDIT_TRAIL is not in this org's tier
        if (!featureFlags.isEnabled(env.getOrgId(), Feature.AUDIT_TRAIL)) {
            log.debug("AUDIT_TRAIL not enabled for org {}, skipping event {}", env.getOrgId(), env.getEventId());
            return;
        }

        AuditEnvelope.Actor actor = env.getActor();
        AuditEnvelope.Target target = env.getTarget();
        AuditEnvelope.Context ctx = env.getContext();

        // Only hash PII if PII_REDACTION is enabled; otherwise omit IP/UA entirely
        boolean piiEnabled = featureFlags.isEnabled(env.getOrgId(), Feature.PII_REDACTION);
        String ipHash = (piiEnabled && ctx != null) ? redact.hashIp(ctx.getIp()) : null;
        String uaHash = (piiEnabled && ctx != null) ? redact.hashUa(ctx.getUa()) : null;

        DomainEvent row = DomainEvent.builder()
                .eventId(env.getEventId())
                .orgId(env.getOrgId())
                .occurredAt(env.getOccurredAt())
                .receivedAt(Instant.now(clock))
                .eventType(env.getEventType())
                .actionLabel(env.getActionLabel())
                .actorId(actor == null ? null : actor.getActorId())
                .actorName(actor == null ? null : actor.getActorName())
                .actorRole(actor == null ? null : actor.getActorRole())
                .actorType(actor == null ? null : actor.getActorType())
                .targetType(target == null ? null : target.getAggregateType())
                .targetId(target == null ? null : target.getAggregateId())
                .targetLabel(target == null ? null : target.getAggregateLabel())
                .severity(env.getSeverity() == null ? "INFO" : env.getSeverity())
                .source(ctx == null ? null : ctx.getSource())
                .correlationId(ctx == null ? null : ctx.getCorrelationId())
                .ipHash(ipHash)
                .uaHash(uaHash)
                .build();

        try {
            repo.save(row);
        } catch (DataIntegrityViolationException dup) {
            // Duplicate eventId: idempotent ignore
        }
    }
}
