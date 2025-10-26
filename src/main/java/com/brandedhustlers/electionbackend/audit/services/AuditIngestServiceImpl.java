package com.brandedhustlers.electionbackend.audit.services;

import com.brandedhustlers.electionbackend.audit.domain.DomainEvent;
import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import com.brandedhustlers.electionbackend.audit.repository.DomainEventRepository;
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
@Service
public class AuditIngestServiceImpl implements AuditIngestService {
    private final DomainEventRepository repo;
    private final RedactionService redact;
    private final Clock clock;

    AuditIngestServiceImpl(DomainEventRepository repo, RedactionService redact) {
        this.repo = repo;
        this.redact = redact;
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

        AuditEnvelope.Actor actor = env.getActor();
        AuditEnvelope.Target target = env.getTarget();
        AuditEnvelope.Context ctx = env.getContext();

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
                .ipHash(ctx == null ? null : redact.hashIp(ctx.getIp()))
                .uaHash(ctx == null ? null : redact.hashUa(ctx.getUa()))
                .build();

        try {
            repo.save(row);
        } catch (DataIntegrityViolationException dup) {
            // Duplicate eventId: idempotent ignore
        }
    }
}