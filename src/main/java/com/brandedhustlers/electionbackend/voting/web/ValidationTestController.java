package com.brandedhustlers.electionbackend.voting.web;

import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/debug")
@Profile("!prod")
public class ValidationTestController {

    private final VoteRepository voteRepository;

    public ValidationTestController(VoteRepository voteRepository) {
        this.voteRepository = voteRepository;
    }

    @PostMapping("/corrupt-vote/{voteId}")
    public ResponseEntity<String> corruptVote(@PathVariable UUID voteId) {
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new IllegalArgumentException("Vote not found"));

        vote.setCandidateId(UUID.randomUUID());
        voteRepository.save(vote);

        return ResponseEntity.ok("Vote corrupted - candidateId changed");
    }

    @PostMapping("/tamper-vote/{voteId}")
    public ResponseEntity<String> tamperWithVote(
            @PathVariable UUID voteId,
            @RequestParam String field,
            @RequestParam String value) {

        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new IllegalArgumentException("Vote not found"));

        switch (field) {
            case "candidateId" -> vote.setCandidateId(UUID.fromString(value));
            case "voterId" -> vote.setVoterId(UUID.fromString(value));
            case "electionId" -> vote.setElectionId(UUID.fromString(value));
            case "dedupeKey" -> vote.setDedupeKey(value);
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }

        voteRepository.save(vote);

        return ResponseEntity.ok("Vote tampered - " + field + " changed to " + value);
    }
}