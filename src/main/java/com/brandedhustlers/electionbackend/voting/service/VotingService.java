package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.domain.Election;
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
import com.brandedhustlers.electionbackend.voting.util.Hash;
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
    private final OutBoxRepository outbox;
    private final ObjectMapper om;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final ElectionVoterRepository evRepository;
    private final Hash getHash;
    private final VoteArchiveRepository voteArchive;
    private final AuditEmitter emitter;
    private final EligibilityService eligibilityService;

    private static final Duration STEP_UP_WINDOW = Duration.ofMinutes(15);


    public VotingService(VoteRepository votes,
                         ElectionVoterRepository evRepo,
                         OutBoxRepository outbox,
                         ObjectMapper om,
                         CandidateRepository candidateRepository,
                         ElectionRepository electionRepository,
                         VoterRepository voterRepository,
                         ElectionVoterRepository evRepository,
                         Hash getHash,
                         VoteArchiveRepository voteArchive, AuditEmitter emitter, EligibilityService eligibilityService) {
        this.votes = votes;
        this.evRepo = evRepo;
        this.candidateRepository = candidateRepository;
        this.voteArchive = voteArchive;
        this.outbox = outbox;
        this.om = om;
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.evRepository = evRepository;
        this.getHash = getHash;
        this.emitter = emitter;
        this.eligibilityService = eligibilityService;
    }

    @Override
    @Transactional
    public VoteAckDTO cast(UUID orgId, UUID voterId, CastVoteCmd cmd, HttpServletRequest httpServletRequest) {

        try {
            Voter actor = voterRepository.findById(voterId)
                    .orElseThrow(() -> new UserNotFound("Voter with ID: " + voterId + " not found."));

            String dedupeKey = Optional.ofNullable(cmd.idempotencyKey())
                    .orElseGet(() -> UUID.randomUUID().toString());

            Election election = electionRepository.findByElectionId(cmd.electionId())
                    .orElseThrow(() -> new IllegalArgumentException("Election with id: " + cmd.electionId() + " not found"));

            Optional<ElectionVoter> existingEv = evRepository.findByVoterIdAndElectionId(voterId, cmd.electionId());

            if (existingEv.isPresent()) {
                return new VoteAckDTO(null, "REJECTED", "ALREADY_VOTED");
            }

            /**
             * Check voter eligibility BEFORE taking row lock.
             * This is fast and prevents ineligible voters from even attempting to vote.
             */
            if (election.getEligibilityPolicy() != null) {
                EligibilityService.EligibilityResult eligibilityResult =
                        eligibilityService.evaluate(actor.getAttributes(), election.getEligibilityPolicy());

                if (!eligibilityResult.isAllowed()) {
                    log.warn("Voter {} ineligible for election {}: {}",
                            voterId, cmd.electionId(), eligibilityResult.getReason());

                    return new VoteAckDTO(
                            null,
                            "REJECTED",
                            "INELIGIBLE: " + eligibilityResult.getReason()
                    );
                }

                log.info("Voter {} passed eligibility check for election {}", voterId, cmd.electionId());
            }

            //create the election voter here instead removing load from org admins, tied to the voter entity
            ElectionVoterId embeddedId = new ElectionVoterId(
                    cmd.electionId(),
                    actor.getVoterId()
            );
            ElectionVoter newVoter = new ElectionVoter(
                    embeddedId,
                    actor,
                    election,
                    null,
                    null,
                    Timestamp.from(Instant.now()),
                    false
            );
            evRepository.save(newVoter);

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
                    .deviceId(null)
                    .proofRef(null)
                    .txId(null)
                    .dedupeKey(dedupeKey)
                    .castAt(Instant.now())
                    .build();
            votes.saveAndFlush(vote);

            ev.setHasVoted(true);
            evRepo.save(ev);

            String voteHash = getHash.hashVote(vote);

            VoteArchive archive = VoteArchive.builder()
                    .voteId(vote.getId())
                    .electionId(vote.getElectionId())
                    .candidateId(vote.getCandidateId())
                    .voterId(vote.getVoterId())
                    .orgId(vote.getOrgId())
                    .dedupeKey(vote.getDedupeKey())
                    .castAt(Timestamp.from(vote.getCastAt()))
                    .voteHash(voteHash)
                    .archivedAt(Timestamp.from(Instant.now()))
                    .build();

            voteArchive.save(archive);
            log.info("Archived vote {} with hash {}", vote.getId(), voteHash);

            Map<String, UUID> payload = Map.of(
                    "id", vote.getId(),
                    "electionId", vote.getElectionId(),
                    "candidateId", vote.getCandidateId(),
                    "orgId", orgId
            );

            outbox.save(OutBox.builder()
                    .eventType("voting.vote.cast")
                    .aggregateType("Vote")
                    .aggregateId(vote.getId())
                    .payload(om.writeValueAsString(payload))
                    .createdAt(Timestamp.from(Instant.now()))
                    .electionId(vote.getElectionId())
                    .voteHash(voteHash)
                    .anchorPending(false)
                    .anchored(false)
                    .build());

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

        } catch (Exception e) {
            log.error("failed to cast vote for voter: {} in election: {}", voterId, cmd.electionId(), e);
            throw new RuntimeException(e);
        }
    }

    public void requireFreshStepUp(HttpSession session) {
        Instant ts = (Instant) session.getAttribute("STEP_UP_VERIFIED_AT");
        if (ts == null) throw new SecurityException("VOTE-401 step-up required");
        if (Duration.between(ts, Instant.now()).compareTo(STEP_UP_WINDOW) > 0) {
            throw new SecurityException("VOTE-401 step-up expired");
        }
    }
}