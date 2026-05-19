package com.brandedhustlers.electionbackend.chain;

import com.brandedhustlers.electionbackend.chain.repository.ChainBatchRepository;
import com.brandedhustlers.electionbackend.chain.service.AnchorService;
import com.brandedhustlers.electionbackend.chain.service.BlockchainProvider;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.FeatureFlagService;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AnchorService")
class AnchorServiceTest {

    @Mock
    private ElectionRepository electionRepo;
    @Mock
    private OutBoxRepository outBoxRepository;
    @Mock
    private ChainBatchRepository batchRepo;
    @Mock
    private BlockchainProvider chain;
    @Mock
    private FeatureFlagService featureFlags;

    private ObjectMapper objectMapper;

    private AnchorService anchorService;
    private UUID orgId;
    private UUID electionId;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        anchorService = new AnchorService(electionRepo, outBoxRepository, batchRepo, chain, featureFlags, objectMapper);
        orgId = UUID.randomUUID();
        electionId = UUID.randomUUID();
    }

    private OutBox outboxEntry(UUID eId) {
        OutBox entry = new OutBox();
        entry.setId(UUID.randomUUID());
        entry.setElectionId(eId);
        entry.setVoteHash("a".repeat(64)); // 64-char hex-like string
        entry.setCreatedAt(Timestamp.from(Instant.now()));
        // Payload with electionId for extractElectionId()
        entry.setPayload("{\"id\":\"" + UUID.randomUUID() + "\","
                + "\"electionId\":\"" + eId + "\","
                + "\"candidateId\":\"" + UUID.randomUUID() + "\","
                + "\"orgId\":\"" + UUID.randomUUID() + "\"}");
        return entry;
    }

    @Nested
    @DisplayName("anchorPendingVotes — empty queue")
    class EmptyQueue {

        @Test
        @DisplayName("Does nothing when no pending outbox entries")
        void doesNothingWhenNoPendingEntries() {
            when(outBoxRepository.findPendingAnchorBatch(pageable))
                    .thenReturn(Collections.emptyList());

            anchorService.anchorPendingVotes();

            verifyNoInteractions(chain, batchRepo, electionRepo, featureFlags);
        }
    }

    @Nested
    @DisplayName("anchorPendingVotes — BLOCKCHAIN_ANCHORING feature gate")
    class BlockchainAnchoringGate {

        @Test
        @DisplayName("Skips anchoring for orgs with BLOCKCHAIN_ANCHORING disabled")
        void skipsOrgWhenFeatureDisabled() {
            OutBox entry = outboxEntry(electionId);
            when(outBoxRepository.findPendingAnchorBatch(pageable))
                    .thenReturn(List.of(entry));
            when(electionRepo.findOrgId(electionId)).thenReturn(orgId);
            when(featureFlags.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING)).thenReturn(false);

            anchorService.anchorPendingVotes();

            verifyNoInteractions(chain);
            verify(batchRepo, never()).save(any());
        }

        @Test
        @DisplayName("Anchors batch when BLOCKCHAIN_ANCHORING is enabled for org")
        void anchorsWhenFeatureEnabled() {
            OutBox entry = outboxEntry(electionId);
            when(outBoxRepository.findPendingAnchorBatch(pageable))
                    .thenReturn(List.of(entry));
            when(electionRepo.findOrgId(electionId)).thenReturn(orgId);
            when(featureFlags.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING)).thenReturn(true);
            when(chain.anchorBatch(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(UUID.randomUUID().toString());

            anchorService.anchorPendingVotes();

            verify(chain).anchorBatch(
                    eq(electionId.toString()), eq(orgId.toString()), anyString(), anyString());
            verify(batchRepo).save(any());
        }

        @Test
        @DisplayName("Processes enabled orgs and skips disabled orgs in the same cycle")
        void processesEnabledSkipsDisabled() {
            UUID enabledElectionId = UUID.randomUUID();
            UUID disabledElectionId = UUID.randomUUID();
            UUID enabledOrgId = UUID.randomUUID();
            UUID disabledOrgId = UUID.randomUUID();

            OutBox enabledEntry = outboxEntry(enabledElectionId);
            OutBox disabledEntry = outboxEntry(disabledElectionId);

            when(outBoxRepository.findPendingAnchorBatch(pageable))
                    .thenReturn(List.of(enabledEntry, disabledEntry));
            when(electionRepo.findOrgId(enabledElectionId)).thenReturn(enabledOrgId);
            when(electionRepo.findOrgId(disabledElectionId)).thenReturn(disabledOrgId);
            when(featureFlags.isEnabled(enabledOrgId, Feature.BLOCKCHAIN_ANCHORING)).thenReturn(true);
            when(featureFlags.isEnabled(disabledOrgId, Feature.BLOCKCHAIN_ANCHORING)).thenReturn(false);
            when(chain.anchorBatch(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(UUID.randomUUID().toString());

            anchorService.anchorPendingVotes();

            // Chain called exactly once — for the enabled org only
            verify(chain, times(1)).anchorBatch(anyString(), anyString(), anyString(), anyString());
            verify(batchRepo, times(1)).save(any());
        }

        @Test
        @DisplayName("Marks outbox entries as anchored after successful batch")
        void marksEntriesAsAnchored() {
            OutBox entry = outboxEntry(electionId);
            when(outBoxRepository.findPendingAnchorBatch(pageable))
                    .thenReturn(List.of(entry));
            when(electionRepo.findOrgId(electionId)).thenReturn(orgId);
            when(featureFlags.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING)).thenReturn(true);
            when(chain.anchorBatch(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(UUID.randomUUID().toString());

            anchorService.anchorPendingVotes();

            verify(outBoxRepository).save(argThat(o -> o.getAnchored() && !o.getAnchorPending()));
        }

        @Test
        @DisplayName("Continues processing other elections when one batch fails")
        void continuesOnBatchFailure() {
            UUID failingElectionId = UUID.randomUUID();
            UUID successElectionId = UUID.randomUUID();
            UUID failingOrgId = UUID.randomUUID();
            UUID successOrgId = UUID.randomUUID();

            OutBox failingEntry = outboxEntry(failingElectionId);
            OutBox successEntry = outboxEntry(successElectionId);

            when(outBoxRepository.findPendingAnchorBatch(pageable))
                    .thenReturn(List.of(failingEntry, successEntry));
            when(electionRepo.findOrgId(failingElectionId)).thenReturn(failingOrgId);
            when(electionRepo.findOrgId(successElectionId)).thenReturn(successOrgId);
            when(featureFlags.isEnabled(any(), eq(Feature.BLOCKCHAIN_ANCHORING))).thenReturn(true);
            when(chain.anchorBatch(eq(failingElectionId.toString()), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Blockchain unreachable"));
            when(chain.anchorBatch(eq(successElectionId.toString()), anyString(), anyString(), anyString()))
                    .thenReturn(UUID.randomUUID().toString());

            // Should not throw — failures are caught per-election
            anchorService.anchorPendingVotes();

            verify(batchRepo, times(1)).save(any());
        }
    }
}
