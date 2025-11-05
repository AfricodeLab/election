package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class VotingService implements IVotingService {
    private final VoteRepository votes;
    private final ElectionVoterRepository evRepo;
    private final VoterIdentityService identity;
    private final OutBoxRepository outbox;
    private final ObjectMapper om;
    private final AuditEmitter emitter;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;

    private static final Duration STEP_UP_WINDOW = Duration.ofMinutes(15);
    private final JWTService jWTService;


    public VotingService(VoteRepository votes,
                         ElectionVoterRepository evRepo,
                         VoterIdentityService identity,
                         OutBoxRepository outbox,
                         ObjectMapper om,
                         AuditEmitter emitter,
                         CandidateRepository candidateRepository,
                         ElectionRepository electionRepository, VoterRepository voterRepository, JWTService jWTService) {
        this.votes = votes;
        this.evRepo = evRepo;
        this.identity = identity;
        this.outbox = outbox;
        this.om = om;
        this.emitter = emitter;
        this.candidateRepository = candidateRepository;
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.jWTService = jWTService;
    }

    @Override
    @Transactional
    public VoteAckDTO cast(UUID orgId, UUID voterId, CastVoteCmd cmd, HttpServletRequest httpServletRequest) {

        Voter actor = voterRepository.findById(voterId)
                .orElseThrow(() -> new UserNotFound("Voter with ID: " + voterId + " not found."));

        String dedupeKey = Optional.ofNullable(cmd.idempotencyKey())
                .orElseGet(() -> UUID.randomUUID().toString());

        var existing = votes.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) {
            return new VoteAckDTO(existing.get().getId(), "ACCEPTED", "IDEMPOTENT");
        }

        // Election is LIVE + in the same org
        var nowTs = new Timestamp(System.currentTimeMillis());
        electionRepository.verifyLiveInOrg(cmd.electionId(), orgId, ElectionStatus.LIVE, nowTs)
                .orElseThrow(() -> new IllegalStateException("Election not live / not in org / outside window"));

        // Candidate belongs to this election
        candidateRepository.findByCandidateIdAndElectionElectionId(cmd.candidateId(), cmd.electionId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid candidate for election"));

        // Pessimistic lock to prevent concurrent double-vote
        ElectionVoter ev = evRepo.lockByIds(cmd.electionId(), voterId)
                .orElseThrow(() -> new IllegalStateException("Voter not assigned to this election"));

        if (ev.isHasVoted()) {
            return new VoteAckDTO(null, "REJECTED", "ALREADY_VOTED");
        }

        //save the vote and flip hasVoted=true
        Vote vote = Vote.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .electionId(cmd.electionId())
                .candidateId(cmd.candidateId())
                .voterId(voterId)
                .dedupeKey(dedupeKey)
                .castAt(new Timestamp(System.currentTimeMillis()))
                .build();
        votes.saveAndFlush(vote);

        ev.setHasVoted(true);
        evRepo.save(ev);

        Map<String, UUID> payload = Map.of(
                "voteId", vote.getId(),
                "electionId", vote.getElectionId(),
                "candidateId", vote.getCandidateId(),
                "orgId", orgId
        );

        log.info("vote payload: {}", payload);

        try {
            outbox.save(OutBox.builder()
                    .eventType("voting.vote.cast")
                    .aggregateType("Vote")
                    .aggregateId(vote.getId())
                    .payload(om.writeValueAsString(payload))
                    .createdAt(java.sql.Timestamp.from(Instant.now()))
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("failed to release vote update ", e);
        }

        emitter.emit(
                null,
                "voting.new vote cast",
                null,
                orgId,
                "New vote",
                voterId,
                actor.getName(),
                "VOTER",
                "VOTER",
                "Voting",
                vote.getId(),
                null,
                "Vote",
                null,
                null,
                null,
                "INFO",
                1
        );
        return new VoteAckDTO(vote.getId(), "ACCEPTED", "NEW_VOTE");
    }

    public void requireFreshStepUp(HttpSession session) {
        java.time.Instant ts = (java.time.Instant) session.getAttribute("STEP_UP_VERIFIED_AT");
        if (ts == null) throw new SecurityException("VOTE-401 step-up required");
        if (java.time.Duration.between(ts, java.time.Instant.now()).compareTo(STEP_UP_WINDOW) > 0) {
            throw new SecurityException("VOTE-401 step-up expired");
        }
    }
}
