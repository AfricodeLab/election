package com.brandedhustlers.electionbackend.audit.consumer;

import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import com.brandedhustlers.electionbackend.audit.services.AuditIngestService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {
    private final AuditIngestService ingest;

    public AuditEventListener(AuditIngestService ingest) {
        this.ingest = ingest;
    }

    /**
     * Listens to normalized audit envelopes. Upstream producers should emit only minimal, dashboard-safe information.
     * Consider a wildcard binding (fan-out) if you mirror domain exchanges, or a dedicated audit.events queue.
     */
    @RabbitListener(queues = "audit.events")
    public void onAuditEvent(AuditEnvelope envelope) {
        ingest.ingest(envelope);
    }
}
