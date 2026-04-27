package com.brandedhustlers.electionbackend.audit.consumer;

import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import com.brandedhustlers.electionbackend.audit.services.AuditIngestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditEventListener {
    private final AuditIngestService ingest;

    public AuditEventListener(AuditIngestService ingest) {
        this.ingest = ingest;
    }

    @RabbitListener(queues = "audit.events")
    public void onAuditEvent(AuditEnvelope envelope) {
        try {
            ingest.ingest(envelope);
        } catch (Exception e) {
            log.error("Failed to ingest audit event {}: {}", envelope != null ? envelope.getEventId() : "null", e.getMessage(), e);
            // Do not rethrow — prevents infinite requeue loop for poison messages
        }
    }
}
