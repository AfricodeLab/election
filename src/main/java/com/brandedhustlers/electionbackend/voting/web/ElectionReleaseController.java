/*
package com.brandedhustlers.electionbackend.voting.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.chain.repository.ChainBatchRepository;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.repository.*;
import com.brandedhustlers.electionbackend.voting.service.ReleaseService;
import com.brandedhustlers.electionbackend.voting.service.ReleaseValidationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/elections")  // Added base path for consistency
@Slf4j
public class ElectionReleaseController {

    private final ElectionRepository electionRepository;
    private final ReleaseValidationService validationService;
    private final ReleaseService releaseService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ElectionConfigAnchorRepository configAnchorRepo;
    private final ElectionConfigSnapshotRepository snapshotRepo;
    private final ChainBatchRepository batchRepo;
    private final TamperingLogRepository tamperingLogRepo;
    private final ElectionResultsAnchorRepository resultsAnchorRepo;
    private final GetUser getUser;
    private final VoteRepository voteRepo;
    private final OutBoxRepository outboxRepo;

    public ElectionReleaseController(
            ElectionRepository electionRepository,
            ReleaseValidationService validationService,
            ReleaseService releaseService,
            ElectionConfigAnchorRepository configAnchorRepo,
            ElectionConfigSnapshotRepository snapshotRepo,
            ChainBatchRepository batchRepo,
            TamperingLogRepository tamperingLogRepo,
            ElectionResultsAnchorRepository resultsAnchorRepo,
            GetUser getUser, VoteRepository voteRepo, OutBoxRepository outboxRepo
    ) {
        this.electionRepository = electionRepository;
        this.validationService = validationService;
        this.releaseService = releaseService;
        this.configAnchorRepo = configAnchorRepo;
        this.snapshotRepo = snapshotRepo;
        this.batchRepo = batchRepo;
        this.tamperingLogRepo = tamperingLogRepo;
        this.resultsAnchorRepo = resultsAnchorRepo;
        this.getUser = getUser;
        this.voteRepo = voteRepo;
        this.outboxRepo = outboxRepo;
    }

    */
/**
     * NEW: Validate release readiness (without streaming).
     * Quick check for UI to show status before starting full validation.
     *//*

    @GetMapping("/{electionId}/validate")
    public ResponseEntity<?> quickValidate(@PathVariable UUID electionId) {
        try {
            verifyAdminAccess(electionId);

            ReleaseValidationService.ReleaseValidationReport report =
                    validationService.validate(electionId, null);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "report", report
            ));

        } catch (Exception e) {
            log.error("Validation failed for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    */
/**
     * Stream validation progress (Server-Sent Events).
     * Real-time updates as each check completes.
     *//*

    @GetMapping(value = "/{electionId}/validate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamValidation(@PathVariable UUID electionId) {
        SseEmitter emitter = new SseEmitter(60000L);

        executor.execute(() -> {
            try {
                // Verify access before streaming
                verifyAdminAccess(electionId);

                // Run validation with progress callbacks
                ReleaseValidationService.ReleaseValidationReport report =
                        validationService.validate(electionId, check -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("validation-progress")
                                        .data(check));
                            } catch (Exception e) {
                                log.error("Failed to send SSE event", e);
                            }
                        });

                // Send completion event
                emitter.send(SseEmitter.event()
                        .name("validation-complete")
                        .data(report));

                emitter.complete();

            } catch (Exception e) {
                log.error("Validation streaming failed for election {}", electionId, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                } catch (Exception ex) {
                    log.error("Failed to send error event", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    */
/**
     * Release election results.
     * Anchors final results to blockchain.
     *//*

    @PostMapping("/{electionId}/release")
    public ResponseEntity<?> releaseResults(
            @PathVariable UUID electionId,
            @RequestParam(defaultValue = "false") boolean forceRelease
    ) {
        try {
            AppUser admin = verifyAdminAccess(electionId);

            log.info("Releasing results for election {}, admin={}, force={}",
                    electionId, admin.getAppUserId(), forceRelease);

            ReleaseService.ReleaseResult result = releaseService.releaseResults(
                    electionId,
                    admin.getAppUserId(),
                    forceRelease
            );

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Results released and anchored to blockchain",
                    "results", result.results(),
                    "validation", result.validation(),
                    "chainTxId", result.chainTxId()
            ));

        } catch (ReleaseService.ReleaseBlockedException e) {
            log.warn("Release blocked for election {}: {}", electionId, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "status", "blocked",
                    "message", e.getMessage(),
                    "validation", e.getValidationReport()
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Access denied for release: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                    "status", "forbidden",
                    "message", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Failed to release results for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Internal server error: " + e.getMessage()
            ));
        }
    }

    */
/**
     * Get audit trail for an election.
     * Shows complete history: config, batches, tampering, results.
     *//*

    @GetMapping("/{electionId}/audit")
    public ResponseEntity<?> getAuditTrail(@PathVariable UUID electionId) {
        try {
            verifyAdminAccess(electionId);

            // Fetch all audit data
            var configAnchor = configAnchorRepo.findById(electionId).orElse(null);
            var snapshots = snapshotRepo.findByElectionIdOrderByCreatedAtAsc(electionId);
            var batches = batchRepo.findByElectionId(electionId);
            var tampering = tamperingLogRepo.findByElectionId(electionId);
            var resultsAnchor = resultsAnchorRepo.findById(electionId).orElse(null);

            return ResponseEntity.ok(Map.of(
                    "electionId", electionId,
                    "configAnchor", configAnchor != null ? configAnchor : Map.of(),
                    "configSnapshots", snapshots,
                    "batchAnchors", batches,
                    "tamperingIncidents", tampering.stream()
                            .map(this::sanitizeTamperingLog)
                            .toList(),
                    "resultsAnchor", resultsAnchor != null ? resultsAnchor : Map.of()
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Access denied for audit trail: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                    "status", "forbidden",
                    "message", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Failed to get audit trail for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    */
/**
     * NEW: Get summary statistics for an election's integrity.
     * Useful for dashboard display.
     *//*

    @GetMapping("/{electionId}/integrity-summary")
    public ResponseEntity<?> getIntegritySummary(@PathVariable UUID electionId) {
        try {
            verifyAdminAccess(electionId);

            long totalVotes = voteRepo.countByElectionId(electionId);
            long anchoredVotes = outboxRepo.countByElectionIdAndAnchoredTrue(electionId);
            long pendingAnchor = outboxRepo.countByElectionIdAndAnchorPendingTrue(electionId);

            var tamperingIncidents = tamperingLogRepo.findByElectionId(electionId);
            long restoredVotes = tamperingIncidents.stream()
                    .filter(t -> "RESTORED".equals(t.getIncidentType()))
                    .count();
            long unrestorableVotes = tamperingIncidents.stream()
                    .filter(t -> "ARCHIVE_TAMPERED".equals(t.getIncidentType()))
                    .count();

            boolean configAnchored = configAnchorRepo.existsById(electionId);
            boolean resultsReleased = resultsAnchorRepo.existsById(electionId);

            long batchCount = batchRepo.countByElectionId(electionId);

            return ResponseEntity.ok(Map.of(
                    "electionId", electionId,
                    "votes", Map.of(
                            "total", totalVotes,
                            "anchored", anchoredVotes,
                            "pendingAnchor", pendingAnchor,
                            "missing", Math.max(0, totalVotes - anchoredVotes - pendingAnchor)
                    ),
                    "tampering", Map.of(
                            "totalIncidents", tamperingIncidents.size(),
                            "restored", restoredVotes,
                            "unrestorable", unrestorableVotes
                    ),
                    "anchors", Map.of(
                            "configAnchored", configAnchored,
                            "batchCount", batchCount,
                            "resultsReleased", resultsReleased
                    ),
                    "status", Map.of(
                            "canRelease", unrestorableVotes == 0 && pendingAnchor == 0,
                            "needsReview", unrestorableVotes > 0 || pendingAnchor > 0
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to get integrity summary for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    */
/**
     * Helper: Verify admin has access to this election.
     *
     * @return The authenticated admin user
     * @throws IllegalArgumentException if not authorized
     *//*

    private AppUser verifyAdminAccess(UUID electionId) {
        Organization electionOrg = electionRepository.getElectionOrganization(electionId);
        AppUser admin = getUser.getCurrentAuthenticatedUser();

        if (!admin.getUserRole().equals(UserRole.SUPER_ADMIN)) {
            throw new IllegalArgumentException("You do not have the authority to access this resource.");
        }

       */
/* if (!admin.getAppUserOrganization().equals(electionOrg)) {
            throw new IllegalArgumentException("You do not belong to this organization and cannot access this election.");
        }*//*
 //todo change this to org admin access

        return admin;
    }

    */
/**
     * Sanitize tampering log for public audit trail.
     * Removes voter PII.
     *//*

    private Map<String, Object> sanitizeTamperingLog(TamperingLog log) {
        return Map.of(
                "voteId", log.getVoteId().toString(),
                "incidentType", log.getIncidentType(),
                "detectedAt", log.getDetectedAt().toString(),
                "resolved", log.getResolved()
                // DO NOT include voterId or other PII
        );
    }

    */
/**
     * Cleanup executor on shutdown.
     *//*

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("Executor service shut down");
    }
}*/
