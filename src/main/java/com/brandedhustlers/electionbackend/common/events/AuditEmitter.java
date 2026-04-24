package com.brandedhustlers.electionbackend.common.events;

import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuditEmitter {
        private final RabbitTemplate rabbitTemplate;
        private final org.springframework.context.ApplicationEventPublisher eventPublisher;

        public AuditEmitter(RabbitTemplate rabbitTemplate,
                        org.springframework.context.ApplicationEventPublisher eventPublisher) {
                this.rabbitTemplate = rabbitTemplate;
                this.eventPublisher = eventPublisher;
        }

        public AuditEnvelope emit(
                        UUID eventId,
                        String eventType,
                        Instant occurredAt,
                        UUID orgId,
                        String actionLabel,
                        UUID actorId,
                        String actorName,
                        String actorRole,
                        String actorType,
                        String aggregateType,
                        UUID aggregateId,
                        String aggregateLabel,
                        String source,
                        String correlationId,
                        String ip,
                        String ua,
                        String severity,
                        Integer version) {
                AuditEnvelope.Actor actor = new AuditEnvelope.Actor(
                                actorId,
                                actorName,
                                actorRole,
                                actorType);

                AuditEnvelope.Target target = new AuditEnvelope.Target(
                                aggregateType,
                                aggregateId,
                                aggregateLabel);

                AuditEnvelope.Context context = new AuditEnvelope.Context(
                                source,
                                correlationId,
                                ip,
                                ua);

                AuditEnvelope envelope = AuditEnvelope.builder()
                                .eventId(eventId == null ? UUID.randomUUID() : eventId)
                                .eventType(eventType)
                                .occurredAt(occurredAt == null ? Instant.now() : occurredAt)
                                .orgId(orgId)
                                .actionLabel(actionLabel)
                                .actor(actor)
                                .target(target)
                                .context(context)
                                .severity(severity == null ? "INFO" : severity)
                                .version(version == null ? 1 : version)
                                .build();

                // Publish to local Spring event bus first
                eventPublisher.publishEvent(envelope);

                return envelope;
        }
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void pushToQueue(AuditEnvelope env) {
                rabbitTemplate.convertAndSend("audit.events", env);
        }
}
