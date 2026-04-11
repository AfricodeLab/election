package com.brandedhustlers.electionbackend.audit;

import com.brandedhustlers.electionbackend.audit.domain.DomainEvent;
import com.brandedhustlers.electionbackend.audit.dto.AuditEnvelope;
import com.brandedhustlers.electionbackend.audit.repository.DomainEventRepository;
import com.brandedhustlers.electionbackend.audit.services.AuditIngestServiceImpl;
import com.brandedhustlers.electionbackend.audit.services.RedactionService;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditIngestService")
class AuditIngestServiceTest {

    @Mock
    private DomainEventRepository repo;
    @Mock
    private RedactionService redactionService;
    @Mock
    private FeatureFlagService featureFlags;

    private AuditIngestServiceImpl service;
    private UUID orgId;
    private AuditEnvelope validEnvelope;

    @BeforeEach
    void setUp() {
        service = new AuditIngestServiceImpl(repo, redactionService, featureFlags);
        orgId = UUID.randomUUID();

        validEnvelope = AuditEnvelope.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .orgId(orgId)
                .eventType("auth.login")
                .actionLabel("User logged in")
                .actor(AuditEnvelope.Actor.builder()
                        .actorId(UUID.randomUUID())
                        .actorName("John Doe")
                        .actorRole("ADMIN")
                        .actorType("APP_USER")
                        .build())
                .context(AuditEnvelope.Context.builder()
                        .ip("192.168.1.1")
                        .ua("Mozilla/5.0")
                        .source("web")
                        .build())
                .build();
    }

    @Nested
    @DisplayName("AUDIT_TRAIL feature gate")
    class AuditTrailGate {

        @Test
        @DisplayName("Saves event when AUDIT_TRAIL is enabled")
        void savesEventWhenEnabled() {
            when(featureFlags.isEnabled(orgId, Feature.AUDIT_TRAIL)).thenReturn(true);
            when(featureFlags.isEnabled(orgId, Feature.PII_REDACTION)).thenReturn(true);
            when(redactionService.hashIp(any())).thenReturn("hashed-ip");
            when(redactionService.hashUa(any())).thenReturn("hashed-ua");

            service.ingest(validEnvelope);

            verify(repo).save(any(DomainEvent.class));
        }

        @Test
        @DisplayName("Skips saving when AUDIT_TRAIL is disabled — no DB write occurs")
        void skipsWhenDisabled() {
            when(featureFlags.isEnabled(orgId, Feature.AUDIT_TRAIL)).thenReturn(false);

            service.ingest(validEnvelope);

            verify(repo, never()).save(any());
            verifyNoInteractions(redactionService);
        }
    }

    @Nested
    @DisplayName("PII_REDACTION feature gate")
    class PiiRedactionGate {

        @Test
        @DisplayName("Hashes IP and UA when PII_REDACTION is enabled")
        void hashesIpAndUaWhenEnabled() {
            when(featureFlags.isEnabled(orgId, Feature.AUDIT_TRAIL)).thenReturn(true);
            when(featureFlags.isEnabled(orgId, Feature.PII_REDACTION)).thenReturn(true);
            when(redactionService.hashIp("192.168.1.1")).thenReturn("ip-hash");
            when(redactionService.hashUa("Mozilla/5.0")).thenReturn("ua-hash");

            service.ingest(validEnvelope);

            ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
            verify(repo).save(captor.capture());

            DomainEvent saved = captor.getValue();
            assertEquals("ip-hash", saved.getIpHash());
            assertEquals("ua-hash", saved.getUaHash());
        }

        @Test
        @DisplayName("Omits IP and UA entirely when PII_REDACTION is disabled")
        void omitsIpAndUaWhenDisabled() {
            when(featureFlags.isEnabled(orgId, Feature.AUDIT_TRAIL)).thenReturn(true);
            when(featureFlags.isEnabled(orgId, Feature.PII_REDACTION)).thenReturn(false);

            service.ingest(validEnvelope);

            ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
            verify(repo).save(captor.capture());

            DomainEvent saved = captor.getValue();
            assertNull(saved.getIpHash(), "IP should not be stored when PII_REDACTION is off");
            assertNull(saved.getUaHash(), "UA should not be stored when PII_REDACTION is off");
            verifyNoInteractions(redactionService);
        }
    }

    @Nested
    @DisplayName("Idempotency and validation")
    class Validation {

        @Test
        @DisplayName("Throws NullPointerException when eventId is missing")
        void throwsWhenEventIdNull() {
            AuditEnvelope bad = AuditEnvelope.builder()
                    .occurredAt(Instant.now())
                    .orgId(orgId)
                    .eventType("auth.login")
                    .actionLabel("Login")
                    .build();

            assertThrows(NullPointerException.class, () -> service.ingest(bad));
        }

        @Test
        @DisplayName("Throws when eventType is blank")
        void throwsWhenEventTypeBlank() {
            // Validation throws before reaching the feature flag check
            AuditEnvelope bad = AuditEnvelope.builder()
                    .eventId(UUID.randomUUID())
                    .occurredAt(Instant.now())
                    .orgId(orgId)
                    .eventType("")
                    .actionLabel("Login")
                    .build();

            assertThrows(IllegalArgumentException.class, () -> service.ingest(bad));
        }

        @Test
        @DisplayName("Silently ignores duplicate eventId (idempotent)")
        void ignoresDuplicateEventId() {
            when(featureFlags.isEnabled(orgId, Feature.AUDIT_TRAIL)).thenReturn(true);
            when(featureFlags.isEnabled(orgId, Feature.PII_REDACTION)).thenReturn(false);
            doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
                    .when(repo).save(any());

            // Should NOT throw — idempotent by design
            assertDoesNotThrow(() -> service.ingest(validEnvelope));
        }

        @Test
        @DisplayName("Saves correct orgId, eventType, and actionLabel")
        void savesCorrectFields() {
            when(featureFlags.isEnabled(orgId, Feature.AUDIT_TRAIL)).thenReturn(true);
            when(featureFlags.isEnabled(orgId, Feature.PII_REDACTION)).thenReturn(false);

            service.ingest(validEnvelope);

            ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
            verify(repo).save(captor.capture());

            DomainEvent saved = captor.getValue();
            assertEquals(orgId, saved.getOrgId());
            assertEquals("auth.login", saved.getEventType());
            assertEquals("User logged in", saved.getActionLabel());
        }
    }
}
