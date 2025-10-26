package com.brandedhustlers.electionbackend.audit.services;

import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;

/**
 * Ingests normalized envelopes; applies hashing/redaction; idempotent by eventId
 */
public interface AuditIngestService {
    void ingest(AuditEnvelope envelope);
}
