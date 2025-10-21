package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoterId;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VotingService implements IVotingService{
    private final VoteRepository votes;
    private final ElectionVoterRepository evRepo;
    private final VoterIdentityService identity;
    private final OutBoxRepository outbox;
    private final ObjectMapper om;

    public VotingService(VoteRepository votes,
                         ElectionVoterRepository evRepo,
                         VoterIdentityService identity,
                         OutBoxRepository outbox,
                         ObjectMapper om) {
        this.votes = votes;
        this.evRepo = evRepo;
        this.identity = identity;
        this.outbox = outbox;
        this.om = om;
    }

    @Override
    @Transactional
    public VoteAckDTO cast(UUID orgId, UUID userId, CastVoteCmd cmd) {
        String dedupeKey = Optional.ofNullable(cmd.idempotencyKey()).orElseGet(() -> UUID.randomUUID().toString());
        if (votes.findByDedupeKey(dedupeKey).isPresent()) {
            var existing = votes.findByDedupeKey(dedupeKey).get();
            return new VoteAckDTO(existing.getId(), "ACCEPTED", "IDEMPOTENT");
        }

        UUID voterId = identity.resolveVoterId(orgId, cmd.electionId(), cmd.voterToken());

        // concurrency gate: ensure not voted
        ElectionVoterId id = new ElectionVoterId(cmd.electionId(), voterId);
        ElectionVoter ev = evRepo.findById(id.getElectionId()).
                orElseThrow(() -> new IllegalStateException("Not assigned"));

        if (ev.isHasVoted()){
            return new VoteAckDTO(null,
                    "REJECTED",
                    "ALREADY_VOTED");
        }

        // TODO validate election LIVE + window (call election module read service)

        Vote vote = Vote.builder()
                .id(UUID.randomUUID()).orgId(orgId)
                .electionId(cmd.electionId()).candidateId(cmd.candidateId())
                .voterId(voterId).deviceId(cmd.deviceId())
                .dedupeKey(dedupeKey)
                .castAt(Timestamp.from(Instant.now()))
                .build();
        votes.saveAndFlush(vote);

        ev.setHasVoted(true);
        evRepo.save(ev);

        // outbox enqueue
        Map<String, Object> payload = Map.of("voteId", vote.getId(), "electionId", vote.getElectionId(), "candidateId", vote.getCandidateId(), "orgId", orgId);
        try {
            outbox.save(OutBox.builder()
                    .id(UUID.randomUUID())
                    .eventType("voting.vote.cast")
                    .aggregateType("Vote")
                    .aggregateId(vote.getId())
                    .payload(om.writeValueAsString(payload))
                    .createdAt(Timestamp.from(Instant.now()))
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new VoteAckDTO(vote.getId(),
                "ACCEPTED",
                null);
    }
}
