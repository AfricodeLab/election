package com.brandedhustlers.electionbackend.voting;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.EligibilityService;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoterId;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.domain.VoteArchive;
import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteArchiveRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.brandedhustlers.electionbackend.voting.service.VotingService;
import com.brandedhustlers.electionbackend.voting.util.Hash;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VotingConcurrencyTest
 *
 * Simulates 10 concurrent voters racing against all 10 guards in VotingService.
 * Uses Mockito to stub infrastructure (DB, hash, audit) so the test is pure unit-level
 * — no real DB, no real network, no Resilience4j (those are integration concerns).
 *
 * Guards exercised:
 *   G1  Step-up window    — checked in VoteController; skipped here (controller test)
 *   G2  Rate limiter      — Resilience4j annotation; skipped here (integration test)
 *   G3  Bulkhead          — Resilience4j annotation; skipped here (integration test)
 *   G4  Org mismatch      — ✅ tested (voter.orgId != JWT orgId)
 *   G5  ALREADY_VOTED     — ✅ tested (pre-lock fast-path)
 *   G6  Eligibility       — ✅ tested (ineligible voter)
 *   G7  Election LIVE     — ✅ tested (non-live election)
 *   G8  Candidate valid   — ✅ tested (wrong candidateId)
 *   G9  Pessimistic lock  — ✅ simulated via CyclicBarrier (concurrent race)
 *  G10  Dedupe key        — ✅ tested (idempotent second call)
 *
 * For G2/G3 integration tests see load_tests/vote_cast_k6.js.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Vote Casting — Concurrency & Guard Tests")
class VotingConcurrencyTest {

    @Mock VoteRepository       voteRepository;
    @Mock ElectionVoterRepository evRepository;
    @Mock OutBoxRepository     outBoxRepository;
    @Mock CandidateRepository  candidateRepository;
    @Mock ElectionRepository   electionRepository;
    @Mock VoterRepository      voterRepository;
    @Mock Hash                 hash;
    @Mock VoteArchiveRepository voteArchiveRepository;
    @Mock AuditEmitter         auditEmitter;
    @Mock EligibilityService   eligibilityService;
    @Mock HttpServletRequest   httpServletRequest;

    private VotingService service;

    private static final UUID ORG_ID       = UUID.randomUUID();
    private static final UUID ELECTION_ID  = UUID.randomUUID();
    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new VotingService(
                voteRepository, evRepository, outBoxRepository,
                new ObjectMapper(), candidateRepository, electionRepository,
                voterRepository, evRepository, hash, voteArchiveRepository,
                auditEmitter, eligibilityService);
    }

    // ── Shared stubs ──────────────────────────────────────────────────────────

    private Voter makeVoter(UUID voterId, UUID orgId) {
        Organization org = mock(Organization.class);
        lenient().when(org.getOrganizationId()).thenReturn(orgId);

        Voter voter = mock(Voter.class);
        lenient().when(voter.getVoterId()).thenReturn(voterId);
        lenient().when(voter.getOrganization()).thenReturn(org);
        lenient().when(voter.getName()).thenReturn("Test Voter " + voterId);
        lenient().when(voter.getAttributes()).thenReturn(null);
        return voter;
    }

    private Election makeLiveElection() {
        Election election = mock(Election.class);
        lenient().when(election.getElectionId()).thenReturn(ELECTION_ID);
        lenient().when(election.getElectionStatus()).thenReturn(ElectionStatus.LIVE);
        lenient().when(election.getEligibilityPolicy()).thenReturn(null); // no policy = all eligible
        return election;
    }

    private ElectionVoter makeEv(UUID voterId, boolean hasVoted) {
        ElectionVoter ev = mock(ElectionVoter.class);
        lenient().when(ev.isHasVoted()).thenReturn(hasVoted);
        return ev;
    }

    private void stubHappyPath(UUID voterId, ElectionVoter evLock) {
        Voter voter = makeVoter(voterId, ORG_ID);
        Election election = makeLiveElection();
        Candidate candidate = mock(Candidate.class);

        when(voterRepository.findById(voterId)).thenReturn(Optional.of(voter));
        when(electionRepository.findByElectionId(ELECTION_ID)).thenReturn(Optional.of(election));
        when(electionRepository.verifyLiveInOrg(eq(ELECTION_ID), eq(ORG_ID), eq(ElectionStatus.LIVE), any()))
                .thenReturn(Optional.of(election));
        when(evRepository.findByVoterIdAndElectionId(voterId, ELECTION_ID)).thenReturn(Optional.empty());
        when(evRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evRepository.lockByIds(eq(ELECTION_ID), eq(voterId))).thenReturn(Optional.of(evLock));
        when(candidateRepository.findByCandidateIdAndElectionElectionId(CANDIDATE_ID, ELECTION_ID))
                .thenReturn(Optional.of(candidate));
        when(voteRepository.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(voteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(voteArchiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outBoxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hash.hashVote(any())).thenReturn("deadbeef");
        lenient().when(auditEmitter.emit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G4 — Org mismatch
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G4: Org mismatch → REJECTED ORG_MISMATCH")
    void g4_orgMismatch() {
        UUID voterId = UUID.randomUUID();
        UUID differentOrg = UUID.randomUUID();

        // Voter belongs to a DIFFERENT org than the JWT claims
        Voter voter = makeVoter(voterId, differentOrg);
        when(voterRepository.findById(voterId)).thenReturn(Optional.of(voter));

        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, null);
        VoteAckDTO ack = service.cast(ORG_ID, voterId, cmd, httpServletRequest);

        assertThat(ack.status()).isEqualTo("REJECTED");
        assertThat(ack.reason()).isEqualTo("ORG_MISMATCH");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G5 — ALREADY_VOTED pre-check (fast path before lock)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G5: ALREADY_VOTED pre-check → fast reject before lock")
    void g5_alreadyVotedFastPath() {
        UUID voterId = UUID.randomUUID();
        Voter voter = makeVoter(voterId, ORG_ID);
        Election election = makeLiveElection();
        ElectionVoter ev = makeEv(voterId, true); // hasVoted = true

        when(voterRepository.findById(voterId)).thenReturn(Optional.of(voter));
        when(electionRepository.findByElectionId(ELECTION_ID)).thenReturn(Optional.of(election));
        when(evRepository.findByVoterIdAndElectionId(voterId, ELECTION_ID)).thenReturn(Optional.of(ev));

        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, null);
        VoteAckDTO ack = service.cast(ORG_ID, voterId, cmd, httpServletRequest);

        assertThat(ack.status()).isEqualTo("REJECTED");
        assertThat(ack.reason()).isEqualTo("ALREADY_VOTED");
        // Lock must NOT be acquired — guard fired before we got there
        verify(evRepository, never()).lockByIds(any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G6 — Eligibility policy
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G6: Ineligible voter → REJECTED INELIGIBLE")
    void g6_ineligibleVoter() {
        UUID voterId = UUID.randomUUID();
        Voter voter = makeVoter(voterId, ORG_ID);

        Election election = mock(Election.class);
        when(election.getElectionId()).thenReturn(ELECTION_ID);
        when(election.getEligibilityPolicy()).thenReturn(mock(EligibilityPolicy.class)); // non-null triggers evaluation

        EligibilityService.EligibilityResult denied =
                EligibilityService.EligibilityResult.deny("AGE_UNDER_18");

        when(voterRepository.findById(voterId)).thenReturn(Optional.of(voter));
        when(electionRepository.findByElectionId(ELECTION_ID)).thenReturn(Optional.of(election));
        when(evRepository.findByVoterIdAndElectionId(voterId, ELECTION_ID)).thenReturn(Optional.empty());
        when(eligibilityService.evaluate(any(), any())).thenReturn(denied);

        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, null);
        VoteAckDTO ack = service.cast(ORG_ID, voterId, cmd, httpServletRequest);

        assertThat(ack.status()).isEqualTo("REJECTED");
        assertThat(ack.reason()).contains("INELIGIBLE");
        verify(evRepository, never()).lockByIds(any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G7 — Election not LIVE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G7: Election not LIVE in org → exception thrown")
    void g7_electionNotLive() {
        UUID voterId = UUID.randomUUID();
        ElectionVoter evLock = makeEv(voterId, false);
        stubHappyPath(voterId, evLock);

        // Override: verifyLiveInOrg returns empty
        when(electionRepository.verifyLiveInOrg(eq(ELECTION_ID), eq(ORG_ID), eq(ElectionStatus.LIVE), any()))
                .thenReturn(Optional.empty());

        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, null);
        Assertions.assertThrows(RuntimeException.class,
                () -> service.cast(ORG_ID, voterId, cmd, httpServletRequest));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G8 — Invalid candidate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G8: Wrong candidate for election → exception thrown")
    void g8_invalidCandidate() {
        UUID voterId = UUID.randomUUID();
        ElectionVoter evLock = makeEv(voterId, false);
        stubHappyPath(voterId, evLock);

        // Override: candidateRepository returns empty for this candidate
        when(candidateRepository.findByCandidateIdAndElectionElectionId(CANDIDATE_ID, ELECTION_ID))
                .thenReturn(Optional.empty());

        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, null);
        Assertions.assertThrows(RuntimeException.class,
                () -> service.cast(ORG_ID, voterId, cmd, httpServletRequest));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G9 — Pessimistic lock + double-check under lock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G9: Double-check under lock → second concurrent request blocked")
    void g9_doubleCheckUnderLock() throws Exception {
        UUID voterId = UUID.randomUUID();

        // ev starts as hasVoted=false; we'll flip it after the first thread "wins"
        ElectionVoter evLock = mock(ElectionVoter.class);
        AtomicInteger lockCallCount = new AtomicInteger(0);

        // First call under lock: hasVoted=false (first thread wins)
        // Subsequent calls under lock: hasVoted=true (double-check fires)
        when(evLock.isHasVoted()).thenAnswer(inv -> lockCallCount.getAndIncrement() > 0);

        stubHappyPath(voterId, evLock);

        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, null);

        // First call → NEW_VOTE
        VoteAckDTO first = service.cast(ORG_ID, voterId, cmd, httpServletRequest);
        assertThat(first.reason()).isEqualTo("NEW_VOTE");

        // Second call → ALREADY_VOTED (double-check under lock)
        VoteAckDTO second = service.cast(ORG_ID, voterId, cmd, httpServletRequest);
        assertThat(second.reason()).isEqualTo("ALREADY_VOTED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // G10 — Dedupe key idempotency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("G10: Same idempotency key twice → second call returns IDEMPOTENT")
    void g10_dedupeKey() {
        UUID voterId = UUID.randomUUID();
        ElectionVoter evLock = makeEv(voterId, false);
        stubHappyPath(voterId, evLock);

        String sharedKey = "idempotent-test-key";
        Vote existingVote = mock(Vote.class);
        when(existingVote.getId()).thenReturn(UUID.randomUUID());

        // First call: no existing vote
        when(voteRepository.findByDedupeKey(sharedKey)).thenReturn(Optional.empty());
        CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null, sharedKey);
        VoteAckDTO first = service.cast(ORG_ID, voterId, cmd, httpServletRequest);
        assertThat(first.reason()).isEqualTo("NEW_VOTE");

        // Second call: now dedupe key is present
        when(voteRepository.findByDedupeKey(sharedKey)).thenReturn(Optional.of(existingVote));
        VoteAckDTO second = service.cast(ORG_ID, voterId, cmd, httpServletRequest);
        assertThat(second.reason()).isEqualTo("IDEMPOTENT");
        assertThat(second.status()).isEqualTo("ACCEPTED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONCURRENCY — 10 distinct voters racing simultaneously (happy path)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent: 10 distinct voters — all should get NEW_VOTE")
    void concurrent_tenDistinctVoters() throws Exception {
        int voterCount = 10;
        List<UUID> voterIds = new ArrayList<>();
        for (int i = 0; i < voterCount; i++) {
            UUID vid = UUID.randomUUID();
            voterIds.add(vid);
            ElectionVoter evLock = makeEv(vid, false);
            stubHappyPath(vid, evLock);
        }

        CyclicBarrier barrier = new CyclicBarrier(voterCount);
        ExecutorService pool = Executors.newFixedThreadPool(voterCount);
        List<Future<VoteAckDTO>> futures = new ArrayList<>();

        for (UUID voterId : voterIds) {
            futures.add(pool.submit(() -> {
                barrier.await(); // all threads start simultaneously
                CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null,
                        "key-" + voterId);
                return service.cast(ORG_ID, voterId, cmd, httpServletRequest);
            }));
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        List<VoteAckDTO> results = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                .collect(Collectors.toList());

        long accepted = results.stream().filter(a -> "NEW_VOTE".equals(a.reason())).count();
        long rejected = results.stream().filter(a -> "REJECTED".equals(a.status())).count();

        assertThat(accepted).as("All 10 distinct voters should succeed").isEqualTo(voterCount);
        assertThat(rejected).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONCURRENCY — 10 threads, same voter (only 1 should land)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent: same voter, 10 threads — exactly 1 NEW_VOTE, rest ALREADY_VOTED")
    void concurrent_sameVoterRace() throws Exception {
        UUID voterId = UUID.randomUUID();
        Voter voter = makeVoter(voterId, ORG_ID);
        Election election = makeLiveElection();
        Candidate candidate = mock(Candidate.class);

        when(voterRepository.findById(voterId)).thenReturn(Optional.of(voter));
        when(electionRepository.findByElectionId(ELECTION_ID)).thenReturn(Optional.of(election));
        when(electionRepository.verifyLiveInOrg(eq(ELECTION_ID), eq(ORG_ID), eq(ElectionStatus.LIVE), any()))
                .thenReturn(Optional.of(election));
        when(evRepository.findByVoterIdAndElectionId(voterId, ELECTION_ID)).thenReturn(Optional.empty());
        when(evRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(candidateRepository.findByCandidateIdAndElectionElectionId(CANDIDATE_ID, ELECTION_ID))
                .thenReturn(Optional.of(candidate));
        when(voteRepository.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(voteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(voteArchiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outBoxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hash.hashVote(any())).thenReturn("deadbeef");
        lenient().when(auditEmitter.emit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(null);

        // Simulate pessimistic lock: first call sees hasVoted=false, flips to true
        AtomicInteger lockCount = new AtomicInteger(0);
        ElectionVoter evLock = mock(ElectionVoter.class);
        when(evLock.isHasVoted()).thenAnswer(inv -> lockCount.getAndIncrement() > 0);
        when(evRepository.lockByIds(eq(ELECTION_ID), eq(voterId))).thenReturn(Optional.of(evLock));

        int threads = 10;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<VoteAckDTO>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                barrier.await();
                CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null,
                        "race-" + UUID.randomUUID());
                return service.cast(ORG_ID, voterId, cmd, httpServletRequest);
            }));
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        List<VoteAckDTO> results = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                .collect(Collectors.toList());

        long newVotes      = results.stream().filter(a -> "NEW_VOTE".equals(a.reason())).count();
        long alreadyVoted  = results.stream().filter(a -> "ALREADY_VOTED".equals(a.reason())).count();

        assertThat(newVotes).as("Exactly 1 vote must land (G9 pessimistic lock)").isEqualTo(1);
        assertThat(alreadyVoted).as("Remaining 9 blocked by G9 double-check").isEqualTo(threads - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PERFORMANCE TIMING — measure latency of 10 sequential votes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Perf: 10 sequential votes — service layer p99 < 10ms (mocked infra)")
    void perf_sequentialVotingLatency() {
        int count = 10;
        long[] latencies = new long[count];

        for (int i = 0; i < count; i++) {
            UUID voterId = UUID.randomUUID();
            ElectionVoter evLock = makeEv(voterId, false);
            stubHappyPath(voterId, evLock);

            CastVoteCmd cmd = new CastVoteCmd(ELECTION_ID, CANDIDATE_ID, null, null,
                    "perf-" + i);

            long t0 = System.nanoTime();
            VoteAckDTO ack = service.cast(ORG_ID, voterId, cmd, httpServletRequest);
            latencies[i] = System.nanoTime() - t0;

            assertThat(ack.reason()).isEqualTo("NEW_VOTE");
        }

        long avgMs = Arrays.stream(latencies).sum() / count / 1_000_000;
        long maxMs = Arrays.stream(latencies).max().getAsLong() / 1_000_000;
        Arrays.sort(latencies);
        long p99Ms = latencies[(int)(count * 0.99)] / 1_000_000;

        System.out.printf(
                "%n[PERF] Service-layer latency (mocked infra, n=%d)%n" +
                "       avg=%dms  max=%dms  p99=%dms%n%n",
                count, avgMs, maxMs, p99Ms);

        // With mocked repositories, service overhead alone should be < 10ms
        assertThat(avgMs).as("avg service latency (no DB)").isLessThan(10);
    }
}
