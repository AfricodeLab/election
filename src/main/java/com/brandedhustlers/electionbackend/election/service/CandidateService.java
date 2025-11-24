package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.UpdateCandidateCmd;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.service.AlertService;
import com.brandedhustlers.electionbackend.voting.service.ConfigSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class CandidateService {

    private final CandidateRepository candidateRepo;
    private final ElectionRepository electionRepo;
    private final ConfigSnapshotService snapshotService;
    private final AlertService alertService;

    public CandidateService(
            CandidateRepository candidateRepo,
            ElectionRepository electionRepo,
            ConfigSnapshotService snapshotService,
            AlertService alertService
    ) {
        this.candidateRepo = candidateRepo;
        this.electionRepo = electionRepo;
        this.snapshotService = snapshotService;
        this.alertService = alertService;
    }

    /**
     * Update a candidate. If election is LIVE, restrictions apply.
     */
    @Transactional
    public Candidate updateCandidate(UUID candidateId, UpdateCandidateCmd cmd, UUID adminId) {
        Candidate candidate = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        Election election = candidate.getElection();

        /**
         * Check if election is currently LIVE (voting in progress).
         */
        if (election.getElectionStatus() == ElectionStatus.LIVE) {
            log.warn("Attempt to modify candidate {} during LIVE election {}",
                    candidateId, election.getElectionId());

            /**
             * ONLY allow candidateName changes during voting.
             * All other fields are locked.
             */
            boolean nameChanged = !Objects.equals(cmd.candidateName(), candidate.getCandidateName());
            boolean positionChanged = !Objects.equals(cmd.candidatePosition(), candidate.getCandidatePosition());

            if (nameChanged) {
                log.warn("Candidate name change during voting: election={}, candidate={}, oldName='{}', newName='{}'",
                        election.getElectionId(), candidateId, candidate.getCandidateName(), cmd.candidateName());

                /**
                 * Step 1: Capture snapshot BEFORE change.
                 * This preserves the "before" state for audit.
                 */
                snapshotService.captureSnapshot(
                        election.getElectionId(),
                        "CHANGE_DETECTED",
                        adminId,
                        String.format("Candidate candidateName changed: '%s' → '%s'",
                                candidate.getCandidateName(), cmd.candidateName())
                );

                /**
                 * Step 2: Apply the change.
                 */
                candidate.setCandidateName(cmd.candidateName());
                candidateRepo.save(candidate);

                /**
                 * Step 3: Mark election for review.
                 * This will trigger a warning during release validation.
                 */
                election.setReviewRequired(true);
                election.setReviewReason(
                        String.format("Candidate '%s' candidateName changed during voting", candidate.getCandidateName())
                );
                electionRepo.save(election);

                /**
                 * Step 4: Alert org admins immediately.
                 */
                alertService.notifyConfigChange(election, candidate, cmd.candidateName());

                log.info("Election {} marked for review due to config change", election.getElectionId());

            } else if (positionChanged) {
                /**
                 * Position change: Allow but log (low risk).
                 * Position is typically just display order, not vote data.
                 */
                log.info("Candidate candidatePosition() changed during voting: {} (allowed)", candidateId);
                candidate.setCandidatePosition(cmd.candidatePosition());
                candidateRepo.save(candidate);

            } else {
                /**
                 * Any other field change: BLOCK.
                 * This includes party, photo, bio, etc.
                 */
                throw new IllegalStateException(
                        "Cannot modify candidate fields other than candidateName/candidatePosition() during voting. " +
                                "Election status: " + election.getElectionStatus()
                );
            }

        } else {
            /**
             * Election not LIVE: Allow all changes freely.
             */
            candidate.setCandidateName(cmd.candidateName());
            candidate.setCandidatePosition(cmd.candidatePosition());
            candidateRepo.save(candidate);

            log.info("Candidate {} updated (election not live)", candidateId);
        }

        return candidate;
    }
}