package com.brandedhustlers.electionbackend.common.events;

import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuditEmitter {
    private final RabbitTemplate rabbitTemplate;

    public AuditEmitter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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
            Integer version
    ){
        AuditEnvelope.Actor actor = new AuditEnvelope.Actor(
                actorId,
                actorName,
                actorRole,
                actorType
        );

        AuditEnvelope.Target target = new AuditEnvelope.Target(
                aggregateType,
                aggregateId,
                aggregateLabel
        );

        AuditEnvelope.Context context = new AuditEnvelope.Context(
                source,
                correlationId,
                ip,
                ua
        );

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
        pushToQueue(envelope);

        return envelope;
    }

    private void pushToQueue(AuditEnvelope env){
        rabbitTemplate.convertAndSend("audit.events", env);
    }
}
