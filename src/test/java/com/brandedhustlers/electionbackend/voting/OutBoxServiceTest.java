package com.brandedhustlers.electionbackend.voting;

import com.brandedhustlers.electionbackend.common.config.messaging.RabbitMQConfig;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.domain.VoteArchive;
import com.brandedhustlers.electionbackend.voting.enums.PublishStatus;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.TamperingLogRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteArchiveRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.brandedhustlers.electionbackend.voting.service.AlertService;
import com.brandedhustlers.electionbackend.voting.service.OutBoxService;
import com.brandedhustlers.electionbackend.voting.util.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutBoxService")
class OutBoxServiceTest {

    @Mock
    private OutBoxRepository outBoxRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Hash hashUtil;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private VoteArchiveRepository voteArchiveRepository;
    @Mock
    private TamperingLogRepository tamperingLogRepository;
    @Mock
    private AlertService alertService;

    private OutBoxService outBoxService;

    private UUID voteId;
    private UUID electionId;
    private UUID orgId;
    private static final String STORED_HASH = "abc123hash";

    @BeforeEach
    void setUp() {
        outBoxService = new OutBoxService(outBoxRepository, rabbitTemplate, hashUtil,
                voteRepository, voteArchiveRepository, tamperingLogRepository, alertService);

        voteId = UUID.randomUUID();
        electionId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }

    private OutBox pendingOutBox() {
        return OutBox.builder()
                .id(UUID.randomUUID())
                .aggregateId(voteId)
                .electionId(electionId)
                .voteHash(STORED_HASH)
                .payload("{\"voteId\":\"" + voteId + "\"}")
                .createdAt(Timestamp.from(Instant.now()))
                .build();
    }

    private Vote tableVote() {
        return Vote.builder()
                .id(voteId)
                .electionId(electionId)
                .orgId(orgId)
                .candidateId(UUID.randomUUID())
                .castAt(Instant.now())
                .build();
    }

    private VoteArchive archive(Vote vote) {
        return VoteArchive.builder()
                .voteId(vote.getId())
                .electionId(vote.getElectionId())
                .candidateId(vote.getCandidateId())
                .voterId(UUID.randomUUID())
                .orgId(orgId)
                .castAt(Timestamp.from(vote.getCastAt()))
                .voteHash(STORED_HASH)
                .build();
    }

    @Nested
    @DisplayName("Clean vote (no tampering)")
    class CleanVote {

        @Test
        @DisplayName("Publishes to RabbitMQ and marks outbox published when hash matches")
        void publishesCleanVote() {
            OutBox entry = pendingOutBox();
            Vote vote = tableVote();

            when(outBoxRepository.findTopByPublishedAtIsNull()).thenReturn(List.of(entry));
            when(voteRepository.findById(voteId)).thenReturn(Optional.of(vote));
            when(hashUtil.hashVote(vote)).thenReturn(STORED_HASH);

            outBoxService.publishPending();

            verify(rabbitTemplate).convertAndSend(
                    eq(RabbitMQConfig.VOTING_VOTE_CAST), eq(entry.getPayload()));
            verify(outBoxRepository, atLeastOnce()).save(argThat(o ->
                    o.getPublishedAt() != null && Boolean.TRUE.equals(o.getAnchorPending())));
            verifyNoInteractions(tamperingLogRepository, alertService);
        }

        @Test
        @DisplayName("Does nothing when outbox queue is empty")
        void doesNothingForEmptyBatch() {
            when(outBoxRepository.findTopByPublishedAtIsNull()).thenReturn(Collections.emptyList());

            outBoxService.publishPending();

            verifyNoInteractions(rabbitTemplate, tamperingLogRepository, alertService);
        }
    }

    @Nested
    @DisplayName("Tampering detected — archive is clean (RESTORED)")
    class TamperingRestored {

        @Test
        @DisplayName("Restores vote from archive, logs RESTORED incident, and publishes")
        void restoresFromArchiveAndPublishes() {
            OutBox entry = pendingOutBox();
            Vote vote = tableVote();
            VoteArchive arc = archive(vote);

            when(outBoxRepository.findTopByPublishedAtIsNull()).thenReturn(List.of(entry));
            when(voteRepository.findById(voteId)).thenReturn(Optional.of(vote));
            // First hash: tampered (mismatch). After restore, the vote is clean but we
            // just proceed to publish regardless (restore + proceed in same iteration).
            when(hashUtil.hashVote(any(Vote.class)))
                    .thenReturn("tampered-hash")          // table vote hash (mismatch)
                    .thenReturn(STORED_HASH);              // archive vote hash (matches)
            when(voteArchiveRepository.findById(voteId)).thenReturn(Optional.of(arc));

            outBoxService.publishPending();

            // Vote should have been restored (saved)
            verify(voteRepository).save(any(Vote.class));

            // Incident should be logged as RESTORED
            ArgumentCaptor<List<TamperingLog>> captor = ArgumentCaptor.forClass(List.class);
            verify(tamperingLogRepository).saveAll(captor.capture());
            List<TamperingLog> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals("RESTORED", saved.get(0).getIncidentType());
            assertTrue(saved.get(0).getResolved());

            // AlertService should be called
            verify(alertService).notifyTamperingDetected(anyList());

            // Still published
            verify(rabbitTemplate).convertAndSend(anyString(), (Object) anyString());
        }
    }

    @Nested
    @DisplayName("Tampering detected — archive also tampered (CRITICAL)")
    class TamperingCritical {

        @Test
        @DisplayName("Marks FAILED_VALIDATION, logs ARCHIVE_TAMPERED, skips publish")
        void marksFailedAndSkipsPublish() {
            OutBox entry = pendingOutBox();
            Vote vote = tableVote();
            VoteArchive arc = archive(vote);
            arc.setVoteHash("also-tampered-hash"); // archive hash also bad

            when(outBoxRepository.findTopByPublishedAtIsNull()).thenReturn(List.of(entry));
            when(voteRepository.findById(voteId)).thenReturn(Optional.of(vote));
            when(hashUtil.hashVote(any(Vote.class)))
                    .thenReturn("tampered-hash")      // table vote (mismatch)
                    .thenReturn("also-tampered-hash"); // archive vote (also mismatch)
            when(voteArchiveRepository.findById(voteId)).thenReturn(Optional.of(arc));

            outBoxService.publishPending();

            // Must be marked as failed
            verify(outBoxRepository).save(argThat(o ->
                    PublishStatus.FAILED_VALIDATION.equals(o.getPublishStatus())));

            // Incident logged as ARCHIVE_TAMPERED, not resolved
            ArgumentCaptor<List<TamperingLog>> captor = ArgumentCaptor.forClass(List.class);
            verify(tamperingLogRepository).saveAll(captor.capture());
            List<TamperingLog> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals("ARCHIVE_TAMPERED", saved.get(0).getIncidentType());
            assertFalse(saved.get(0).getResolved());

            // Nothing published
            verifyNoInteractions(rabbitTemplate);

            // Alert still fired
            verify(alertService).notifyTamperingDetected(anyList());
        }
    }

    @Nested
    @DisplayName("Batch resilience")
    class BatchResilience {

        @Test
        @DisplayName("Continues processing subsequent entries when one throws an exception")
        void continuesOnEntryFailure() {
            // Use distinct vote IDs so stubs don't conflict
            UUID failingVoteId = UUID.randomUUID();
            UUID successVoteId = UUID.randomUUID();

            OutBox failing = OutBox.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(failingVoteId)
                    .electionId(electionId)
                    .voteHash(STORED_HASH)
                    .payload("{\"voteId\":\"" + failingVoteId + "\"}")
                    .createdAt(Timestamp.from(Instant.now()))
                    .build();

            OutBox succeeding = OutBox.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(successVoteId)
                    .electionId(electionId)
                    .voteHash(STORED_HASH)
                    .payload("{\"voteId\":\"" + successVoteId + "\"}")
                    .createdAt(Timestamp.from(Instant.now()))
                    .build();

            Vote successVote = Vote.builder().id(successVoteId)
                    .electionId(electionId).orgId(orgId)
                    .candidateId(UUID.randomUUID()).castAt(Instant.now()).build();

            when(outBoxRepository.findTopByPublishedAtIsNull()).thenReturn(List.of(failing, succeeding));
            // First entry: vote not found -> exception caught per-entry
            when(voteRepository.findById(failingVoteId))
                    .thenReturn(Optional.empty()); // orElseThrow fires, caught in loop
            when(voteRepository.findById(successVoteId)).thenReturn(Optional.of(successVote));
            when(hashUtil.hashVote(successVote)).thenReturn(STORED_HASH);

            // Should not throw — failure is caught per-entry
            assertDoesNotThrow(() -> outBoxService.publishPending());

            // Second entry should still be published
            verify(rabbitTemplate, times(1)).convertAndSend(anyString(), (Object) anyString());
        }
    }
}
