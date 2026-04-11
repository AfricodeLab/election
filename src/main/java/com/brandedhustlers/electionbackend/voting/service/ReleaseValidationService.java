package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.EligibilityService;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voting.domain.ElectionConfigSnapshot;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.repository.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class ReleaseValidationService {

    private final ElectionRepository electionRepo;
    private final ElectionConfigAnchorRepository configAnchorRepo;
    private final ElectionConfigSnapshotRepository snapshotRepo;
    private final VoteRepository voteRepo;
    private final OutBoxRepository outbox;
    private final TamperingLogRepository tamperingLog;
    private final ConfigAnchorService configAnchorService;
    private final EligibilityService eligibilityService;
    private final VoterRepository voterRepo;

    public ReleaseValidationService(
            ElectionRepository electionRepo,
            ElectionConfigAnchorRepository configAnchorRepo,
            ElectionConfigSnapshotRepository snapshotRepo,
            VoteRepository voteRepo,
            OutBoxRepository outbox,
            TamperingLogRepository tamperingLog,
            ConfigAnchorService configAnchorService,
            EligibilityService eligibilityService,
            VoterRepository voterRepo
    ) {
        this.electionRepo = electionRepo;
        this.configAnchorRepo = configAnchorRepo;
        this.snapshotRepo = snapshotRepo;
        this.voteRepo = voteRepo;
        this.outbox = outbox;
        this.tamperingLog = tamperingLog;
        this.configAnchorService = configAnchorService;
        this.eligibilityService = eligibilityService;
        this.voterRepo = voterRepo;
    }

    /**
     * Run full release validation.
     *
     * @param electionId The election to validate
     * @param progressCallback Called after each check completes (for UI updates)
     * @return Complete validation report
     */
    @Transactional(readOnly = true)
    public ReleaseValidationReport validate(UUID electionId, Consumer<ValidationCheck> progressCallback) {

        ReleaseValidationReport report = new ReleaseValidationReport();
        report.setElectionId(electionId);
        report.setValidatedAt(Instant.now());

        // Check 1: Config Integrity
        ValidationCheck configCheck = runConfigIntegrityCheck(electionId);
        report.addCheck(configCheck);
        if (progressCallback != null) progressCallback.accept(configCheck);

        // Check 2: Vote Coverage
        ValidationCheck coverageCheck = runVoteCoverageCheck(electionId);
        report.addCheck(coverageCheck);
        if (progressCallback != null) progressCallback.accept(coverageCheck);

        // Check 3: Tampering Detection
        ValidationCheck tamperingCheck = runTamperingCheck(electionId);
        report.addCheck(tamperingCheck);
        if (progressCallback != null) progressCallback.accept(tamperingCheck);

        // Check 4: Review Status
        ValidationCheck reviewCheck = runReviewStatusCheck(electionId);
        report.addCheck(reviewCheck);
        if (progressCallback != null) progressCallback.accept(reviewCheck);

        // NEW: Add eligibility re-check
        ValidationCheck eligibilityCheck = runEligibilityRecheck(electionId);
        report.addCheck(eligibilityCheck);
        if (progressCallback != null) progressCallback.accept(eligibilityCheck);

        // Compute overall status
        report.computeOverallStatus();

        return report;
    }

    /**
     * Check 1: Config Integrity.
     * Compare current config to anchored config.
     */
    private ValidationCheck runConfigIntegrityCheck(UUID electionId) {
        ValidationCheck check = new ValidationCheck(
                "config_integrity",
                "Configuration Integrity",
                ValidationStatus.IN_PROGRESS
        );

        try {
            // Verify config was anchored
            if (!configAnchorRepo.existsById(electionId)) {
                check.setStatus(ValidationStatus.BLOCKED);
                check.setMessage("No config anchor found - was election started properly?");
                check.setCompletedAt(Instant.now());
                return check;
            }

            // Validate current vs anchored
            ConfigAnchorService.ConfigValidationResult result =
                    configAnchorService.validateConfig(electionId);

            if (result.matches()) {
                check.setStatus(ValidationStatus.PASS);
                check.setMessage("Config unchanged since go-live");
            } else {
                check.setStatus(ValidationStatus.WARNING);
                check.setMessage("Config has changed since go-live");
                check.setDetails(Map.of(
                        "anchoredConfig", result.anchoredConfig(),
                        "currentConfig", result.currentConfig()
                ));
            }

            // Also check for snapshots (manual changes)
            List<ElectionConfigSnapshot> snapshots = snapshotRepo
                    .findByElectionIdAndSnapshotTypeOrderByCreatedAtAsc(electionId, "CHANGE_DETECTED");

            if (!snapshots.isEmpty()) {
                check.setStatus(ValidationStatus.WARNING);
                check.setMessage(snapshots.size() + " config change(s) detected during voting");
                check.getDetails().put("changes", snapshots.stream()
                        .map(s -> Map.of(
                                "changedAt", s.getCreatedAt(),
                                "reason", s.getChangeReason()
                        ))
                        .toList());
            }

        } catch (Exception e) {
            check.setStatus(ValidationStatus.BLOCKED);
            check.setMessage("Config validation failed: " + e.getMessage());
            log.error("Config check failed for election {}", electionId, e);
        }

        check.setCompletedAt(Instant.now());
        return check;
    }

    /**
     * Check 2: Vote Coverage.
     * Ensure all votes are anchored.
     */
    private ValidationCheck runVoteCoverageCheck(UUID electionId) {
        ValidationCheck check = new ValidationCheck(
                "vote_coverage",
                "Vote Coverage",
                ValidationStatus.IN_PROGRESS
        );

        try {
            long totalVotesCast = voteRepo.countByElectionId(electionId);
            long totalVotesAnchored = outbox.countByElectionIdAndAnchoredTrue(electionId);
            long pendingAnchor = outbox.countByElectionIdAndAnchorPendingTrue(electionId);

            if (pendingAnchor > 0) {
                check.setStatus(ValidationStatus.WARNING);
                check.setMessage(pendingAnchor + " vote(s) pending anchor (wait 30s or proceed)");
            } else if (totalVotesCast == totalVotesAnchored) {
                check.setStatus(ValidationStatus.PASS);
                check.setMessage("All votes anchored");
            } else {
                long missing = totalVotesCast - totalVotesAnchored - pendingAnchor;
                check.setStatus(ValidationStatus.BLOCKED);
                check.setMessage(missing + " vote(s) missing from blockchain");
            }

            check.setDetails(Map.of(
                    "totalVotesCast", totalVotesCast,
                    "anchored", totalVotesAnchored,
                    "pending", pendingAnchor
            ));

        } catch (Exception e) {
            check.setStatus(ValidationStatus.BLOCKED);
            check.setMessage("Coverage check failed: " + e.getMessage());
            log.error("Coverage check failed for election {}", electionId, e);
        }

        check.setCompletedAt(Instant.now());
        return check;
    }

    /**
     * Check 3: Tampering Detection.
     * Check for any unrestorable votes.
     */
    private ValidationCheck runTamperingCheck(UUID electionId) {
        ValidationCheck check = new ValidationCheck(
                "tampering_detection",
                "Tampering Detection",
                ValidationStatus.IN_PROGRESS
        );

        try {
            List<TamperingLog> incidents = tamperingLog.findByElectionId(electionId);

            long restored = incidents.stream()
                    .filter(i -> "RESTORED".equals(i.getIncidentType()))
                    .count();

            long unrestorable = incidents.stream()
                    .filter(i -> "ARCHIVE_TAMPERED".equals(i.getIncidentType()))
                    .count();

            if (unrestorable > 0) {
                check.setStatus(ValidationStatus.BLOCKED);
                check.setMessage(unrestorable + " vote(s) could not be restored - critical integrity failure");
            } else if (restored > 0) {
                check.setStatus(ValidationStatus.WARNING);
                check.setMessage(restored + " vote(s) were tampered with but successfully restored");
            } else {
                check.setStatus(ValidationStatus.PASS);
                check.setMessage("No tampering detected");
            }

            check.setDetails(Map.of(
                    "totalIncidents", incidents.size(),
                    "restored", restored,
                    "unrestorable", unrestorable,
                    "recentIncidents", incidents.stream()
                            .limit(5)
                            .map(i -> Map.of(
                                    "voteId", i.getVoteId().toString(),
                                    "type", i.getIncidentType(),
                                    "detectedAt", i.getDetectedAt().toString()
                            ))
                            .toList()
            ));

        } catch (Exception e) {
            check.setStatus(ValidationStatus.BLOCKED);
            check.setMessage("Tampering check failed: " + e.getMessage());
            log.error("Tampering check failed for election {}", electionId, e);
        }

        check.setCompletedAt(Instant.now());
        return check;
    }

    /**
     * Check 4: Review Status.
     * Check if election is flagged for manual review.
     */
    private ValidationCheck runReviewStatusCheck(UUID electionId) {
        ValidationCheck check = new ValidationCheck(
                "review_status",
                "Manual Review Status",
                ValidationStatus.IN_PROGRESS
        );

        try {
            Election election = electionRepo.findById(electionId).orElseThrow();

            if (election.getReviewRequired() != null && election.getReviewRequired()) {
                check.setStatus(ValidationStatus.WARNING);
                check.setMessage("Election flagged for review: " + election.getReviewReason());
                check.setDetails(Map.of("reason", election.getReviewReason()));
            } else {
                check.setStatus(ValidationStatus.PASS);
                check.setMessage("No review required");
            }

        } catch (Exception e) {
            check.setStatus(ValidationStatus.BLOCKED);
            check.setMessage("Review check failed: " + e.getMessage());
            log.error("Review check failed for election {}", electionId, e);
        }

        check.setCompletedAt(Instant.now());
        return check;
    }

    /**
     * NEW METHOD: Re-check eligibility for all votes.
     * Detects if voter attributes changed after they voted.
     */
    private ValidationCheck runEligibilityRecheck(UUID electionId) {
        ValidationCheck check = new ValidationCheck(
                "eligibility_recheck",
                "Eligibility Re-validation",
                ValidationStatus.IN_PROGRESS
        );

        try {
            Election election = electionRepo.findById(electionId).orElseThrow();
            EligibilityPolicy policy = election.getEligibilityPolicy();

            if (policy == null) {
                check.setStatus(ValidationStatus.PASS);
                check.setMessage("No eligibility policy defined");
                check.setCompletedAt(Instant.now());
                return check;
            }

            // Get all votes for this election
            List<Vote> votes = voteRepo.findByElectionId(electionId);

            int totalVotes = votes.size();
            int nowIneligible = 0;
            List<Map<String, Object>> driftCases = new ArrayList<>();

            for (Vote vote : votes) {
                Voter voter = voterRepo.findById(vote.getVoterId()).orElse(null);
                if (voter == null) {
                    log.warn("Voter not found for vote {}", vote.getId());
                    continue;
                }

                // Re-evaluate eligibility NOW (with current attributes)
                EligibilityService.EligibilityResult result =
                        eligibilityService.evaluate(voter.getAttributes(), policy);

                if (!result.isAllowed()) {
                    // Voter was eligible at cast time, but is now ineligible
                    nowIneligible++;

                    driftCases.add(Map.of(
                            "voteId", vote.getId().toString(),
                            "voterId", vote.getVoterId().toString(),
                            "voterName", voter.getName(),
                            "reason", result.getReason(),
                            "castAt", vote.getCastAt().toString()
                    ));
                }
            }

            if (nowIneligible > 0) {
                check.setStatus(ValidationStatus.WARNING);
                check.setMessage(
                        String.format("%d of %d voter(s) (%.1f%%) voted when eligible but are now ineligible due to attribute changes",
                                nowIneligible, totalVotes, (nowIneligible * 100.0 / totalVotes))
                );
                check.setDetails(Map.of(
                        "totalVotes", totalVotes,
                        "driftCount", nowIneligible,
                        "policy", "Votes are counted (eligibility evaluated at cast time)",
                        "driftCases", driftCases.stream().limit(20).toList(),  // First 20 for review
                        "note", "These voters were eligible when they voted. Attribute changes occurred after voting."
                ));
            } else {
                check.setStatus(ValidationStatus.PASS);
                check.setMessage(String.format("All %d voters still meet eligibility requirements", totalVotes));
            }

        } catch (Exception e) {
            log.error("Eligibility recheck failed for election {}", electionId, e);
            check.setStatus(ValidationStatus.BLOCKED);
            check.setMessage("Eligibility recheck failed: " + e.getMessage());
        }

        check.setCompletedAt(Instant.now());
        return check;
    }

    /**
     * Validation status enum.
     */
    public enum ValidationStatus {
        PENDING,
        IN_PROGRESS,
        PASS,
        WARNING,
        BLOCKED
    }

    /**
     * Individual validation check.
     */
    @Data
    public static class ValidationCheck {
        private String checkId;
        private String checkName;
        private ValidationStatus status;
        private String message;
        private Map<String, Object> details;
        private Instant completedAt;

        public ValidationCheck(String checkId, String checkName, ValidationStatus status) {
            this.checkId = checkId;
            this.checkName = checkName;
            this.status = status;
            this.details = new HashMap<>();
        }
    }

    /**
     * Complete validation report.
     */
    @Data
    public static class ReleaseValidationReport {
        private UUID electionId;
        private ValidationStatus overallStatus;
        private List<ValidationCheck> checks = new ArrayList<>();
        private boolean autoReleaseBlocked;
        private Instant validatedAt;

        public void addCheck(ValidationCheck check) {
            checks.add(check);
        }

        public void computeOverallStatus() {
            boolean hasBlocked = checks.stream()
                    .anyMatch(c -> c.getStatus() == ValidationStatus.BLOCKED);
            boolean hasWarning = checks.stream()
                    .anyMatch(c -> c.getStatus() == ValidationStatus.WARNING);

            if (hasBlocked) {
                this.overallStatus = ValidationStatus.BLOCKED;
                this.autoReleaseBlocked = true;
            } else if (hasWarning) {
                this.overallStatus = ValidationStatus.WARNING;
                this.autoReleaseBlocked = false;
            } else {
                this.overallStatus = ValidationStatus.PASS;
                this.autoReleaseBlocked = false;
            }
        }
    }
}

