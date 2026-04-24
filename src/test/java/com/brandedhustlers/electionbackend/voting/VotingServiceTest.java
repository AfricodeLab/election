package com.brandedhustlers.electionbackend.voting;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.EligibilityService;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteArchiveRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.brandedhustlers.electionbackend.voting.service.VotingService;
import com.brandedhustlers.electionbackend.voting.util.Hash;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VotingService - the core vote casting logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VotingServiceTest {

        @Mock
        private VoteRepository voteRepository;
        @Mock
        private ElectionVoterRepository evRepo;
        @Mock
        private OutBoxRepository outBoxRepository;
        @Mock
        private ObjectMapper objectMapper;
        @Mock
        private CandidateRepository candidateRepository;
        @Mock
        private ElectionRepository electionRepository;
        @Mock
        private VoterRepository voterRepository;
        @Mock
        private Hash hash;
        @Mock
        private VoteArchiveRepository voteArchiveRepository;
        @Mock
        private AuditEmitter auditEmitter;
        @Mock
        private EligibilityService eligibilityService;
        @Mock
        private HttpServletRequest httpServletRequest;

        private VotingService votingService;

        // Test fixtures
        private UUID orgId;
        private UUID voterId;
        private UUID electionId;
        private UUID candidateId;
        private Voter testVoter;
        private Election testElection;
        private Candidate testCandidate;
        private Organization testOrg;

        @BeforeEach
        void setUp() {
                // Manually construct VotingService to avoid double-injection issues
                votingService = new VotingService(
                                voteRepository,
                                evRepo, // used for lockByIds
                                outBoxRepository,
                                objectMapper,
                                candidateRepository,
                                electionRepository,
                                voterRepository,
                                evRepo, // used for findByVoterIdAndElectionId and save (same instance)
                                hash,
                                voteArchiveRepository,
                                auditEmitter,
                                eligibilityService);

                orgId = UUID.randomUUID();
                voterId = UUID.randomUUID();
                electionId = UUID.randomUUID();
                candidateId = UUID.randomUUID();

                testOrg = new Organization();
                testOrg.setOrganizationId(orgId);
                testOrg.setOrganizationName("Test Org");

                testVoter = Voter.builder()
                                .voterId(voterId)
                                .name("Test Voter")
                                .email("voter@test.com")
                                .phone("1234567890")
                                .nationalId("GHA-123")
                                .organization(testOrg)
                                .build();

                testElection = Election.builder()
                                .electionId(electionId)
                                .electionTitle("Test Election")
                                .electionStatus(ElectionStatus.LIVE)
                                .organization(testOrg)
                                .build();

                testCandidate = Candidate.builder()
                                .candidateId(candidateId)
                                .candidateName("Test Candidate")
                                .affiliation("Party A")
                                .election(testElection)
                                .build();
        }

        @Test
        @DisplayName("Cast vote successfully - new vote is created and saved")
        void testCastVote_Success() throws Exception {
                // Arrange
                CastVoteCmd cmd = new CastVoteCmd(electionId, candidateId, null, null, "idempotency-key-123");

                when(voterRepository.findById(voterId)).thenReturn(Optional.of(testVoter));
                when(electionRepository.findByElectionId(electionId)).thenReturn(Optional.of(testElection));
                when(evRepo.findByVoterIdAndElectionId(voterId, electionId)).thenReturn(Optional.empty());
                when(voteRepository.findByDedupeKey(anyString())).thenReturn(Optional.empty());
                when(electionRepository.verifyLiveInOrg(eq(electionId), eq(orgId), eq(ElectionStatus.LIVE), any()))
                                .thenReturn(Optional.of(testElection));
                when(candidateRepository.findByCandidateIdAndElectionElectionId(candidateId, electionId))
                                .thenReturn(Optional.of(testCandidate));

                ElectionVoter ev = new ElectionVoter();
                ev.setHasVoted(false);
                when(evRepo.lockByIds(electionId, voterId)).thenReturn(Optional.of(ev));
                when(evRepo.save(any(ElectionVoter.class))).thenAnswer(i -> i.getArgument(0));

                when(hash.hashVote(any(Vote.class))).thenReturn("abc123hash");
                when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(i -> i.getArgument(0));
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");

                // Act
                VoteAckDTO result = votingService.cast(orgId, voterId, cmd, httpServletRequest, true);

                // Assert
                assertNotNull(result);
                assertEquals("ACCEPTED", result.status());
                assertEquals("NEW_VOTE", result.reason());
                assertNotNull(result.voteId());

                verify(voteRepository, times(1)).saveAndFlush(any(Vote.class));
                verify(outBoxRepository, times(1)).save(any());
                verify(voteArchiveRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Cast vote rejected - voter already voted in this election")
        void testCastVote_AlreadyVotedInElection() {
                // Arrange
                CastVoteCmd cmd = new CastVoteCmd(electionId, candidateId, null, null, null);

                when(voterRepository.findById(voterId)).thenReturn(Optional.of(testVoter));
                when(electionRepository.findByElectionId(electionId)).thenReturn(Optional.of(testElection));

                // ElectionVoter already exists - voter already voted
                ElectionVoter existingEv = new ElectionVoter();
                existingEv.setHasVoted(true);
                when(evRepo.findByVoterIdAndElectionId(voterId, electionId)).thenReturn(Optional.of(existingEv));

                // Act
                VoteAckDTO result = votingService.cast(orgId, voterId, cmd, httpServletRequest, true);

                // Assert
                assertNotNull(result);
                assertEquals("REJECTED", result.status());
                assertEquals("ALREADY_VOTED", result.reason());
                assertNull(result.voteId());

                // Vote should NOT be saved
                verify(voteRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Cast vote idempotent - same key returns existing vote")
        void testCastVote_Idempotent() throws Exception {
                // Arrange
                String idempotencyKey = "duplicate-key-456";
                CastVoteCmd cmd = new CastVoteCmd(electionId, candidateId, null, null, idempotencyKey);

                Vote existingVote = Vote.builder()
                                .id(UUID.randomUUID())
                                .electionId(electionId)
                                .candidateId(candidateId)
                                .voterId(voterId)
                                .dedupeKey(idempotencyKey)
                                .castAt(Instant.now())
                                .build();

                when(voterRepository.findById(voterId)).thenReturn(Optional.of(testVoter));
                when(electionRepository.findByElectionId(electionId)).thenReturn(Optional.of(testElection));
                when(evRepo.findByVoterIdAndElectionId(voterId, electionId)).thenReturn(Optional.empty());
                when(evRepo.save(any(ElectionVoter.class))).thenAnswer(i -> i.getArgument(0));
                when(voteRepository.findByDedupeKey(idempotencyKey)).thenReturn(Optional.of(existingVote));

                // Act
                VoteAckDTO result = votingService.cast(orgId, voterId, cmd, httpServletRequest, true);

                // Assert
                assertNotNull(result);
                assertEquals("ACCEPTED", result.status());
                assertEquals("IDEMPOTENT", result.reason());
                assertEquals(existingVote.getId(), result.voteId());

                // No new vote should be created
                verify(voteRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Cast vote fails - election not live")
        void testCastVote_ElectionNotLive() {
                // Arrange
                CastVoteCmd cmd = new CastVoteCmd(electionId, candidateId, null, null, null);

                when(voterRepository.findById(voterId)).thenReturn(Optional.of(testVoter));
                when(electionRepository.findByElectionId(electionId)).thenReturn(Optional.of(testElection));
                when(evRepo.findByVoterIdAndElectionId(voterId, electionId)).thenReturn(Optional.empty());
                when(evRepo.save(any(ElectionVoter.class))).thenAnswer(i -> i.getArgument(0));
                when(voteRepository.findByDedupeKey(any())).thenReturn(Optional.empty());

                // Election is not live
                when(electionRepository.verifyLiveInOrg(eq(electionId), eq(orgId), eq(ElectionStatus.LIVE), any()))
                                .thenReturn(Optional.empty());

                // Act & Assert
                RuntimeException exception = assertThrows(RuntimeException.class,
                                () -> votingService.cast(orgId, voterId, cmd, httpServletRequest, true));

                assertTrue(exception.getCause().getMessage().contains("not live"));
        }

        @Test
        @DisplayName("Cast vote fails - voter not found")
        void testCastVote_VoterNotFound() {
                // Arrange
                CastVoteCmd cmd = new CastVoteCmd(electionId, candidateId, null, null, null);

                when(voterRepository.findById(voterId)).thenReturn(Optional.empty());

                // Act & Assert
                RuntimeException exception = assertThrows(RuntimeException.class,
                                () -> votingService.cast(orgId, voterId, cmd, httpServletRequest, true));

                assertTrue(exception.getCause() instanceof UserNotFound);
        }

        @Test
        @DisplayName("Cast vote rejected - hasVoted already true (race condition prevented)")
        void testCastVote_ConcurrentDoubleVote() {
                // Arrange
                CastVoteCmd cmd = new CastVoteCmd(electionId, candidateId, null, null, null);

                when(voterRepository.findById(voterId)).thenReturn(Optional.of(testVoter));
                when(electionRepository.findByElectionId(electionId)).thenReturn(Optional.of(testElection));
                when(evRepo.findByVoterIdAndElectionId(voterId, electionId)).thenReturn(Optional.empty());
                when(evRepo.save(any(ElectionVoter.class))).thenAnswer(i -> i.getArgument(0));
                when(voteRepository.findByDedupeKey(any())).thenReturn(Optional.empty());
                when(electionRepository.verifyLiveInOrg(eq(electionId), eq(orgId), eq(ElectionStatus.LIVE), any()))
                                .thenReturn(Optional.of(testElection));
                when(candidateRepository.findByCandidateIdAndElectionElectionId(candidateId, electionId))
                                .thenReturn(Optional.of(testCandidate));

                // Pessimistic lock returns voter who has already voted (race condition caught)
                ElectionVoter ev = new ElectionVoter();
                ev.setHasVoted(true);
                when(evRepo.lockByIds(electionId, voterId)).thenReturn(Optional.of(ev));

                // Act
                VoteAckDTO result = votingService.cast(orgId, voterId, cmd, httpServletRequest, true);

                // Assert
                assertNotNull(result);
                assertEquals("REJECTED", result.status());
                assertEquals("ALREADY_VOTED", result.reason());

                // Vote should NOT be saved
                verify(voteRepository, never()).saveAndFlush(any());
        }
}
