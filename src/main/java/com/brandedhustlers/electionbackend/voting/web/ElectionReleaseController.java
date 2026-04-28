package com.brandedhustlers.electionbackend.voting.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.chain.repository.ChainBatchRepository;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
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
@RequestMapping("/api/v1/elections")
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
    private final Util util;
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
            GetUser getUser,
            Util util,
            VoteRepository voteRepo,
            OutBoxRepository outboxRepo
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
        this.util = util;
        this.voteRepo = voteRepo;
        this.outboxRepo = outboxRepo;
    }

    /**
     * Quick validation check — no streaming, returns full report at once.
     * Used by the UI to show pre-release integrity status.
     */
    @GetMapping("/{electionId}/validate")
    public ResponseEntity<?> quickValidate(@PathVariable UUID electionId) {
        try {
            AppUser actor = verifyReleaseAccess(electionId);
            log.info("Validation requested for election {} by {}", electionId, actor.getAppUserId());

            ReleaseValidationService.ReleaseValidationReport report =
                    validationService.validate(electionId, null);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "report", report
            ));
        } catch (com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("status", "forbidden", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Validation failed for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Stream validation progress via Server-Sent Events.
     * Each check fires an event as it completes.
     */
    @GetMapping(value = "/{electionId}/validate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamValidation(@PathVariable UUID electionId) {
        SseEmitter emitter = new SseEmitter(60_000L);

        executor.execute(() -> {
            try {
                verifyReleaseAccess(electionId);

                ReleaseValidationService.ReleaseValidationReport report =
                        validationService.validate(electionId, check -> {
                            try {
                                emitter.send(SseEmitter.event().name("validation-progress").data(check));
                            } catch (Exception e) {
                                log.warn("Failed to send SSE progress event", e);
                            }
                        });

                emitter.send(SseEmitter.event().name("validation-complete").data(report));
                emitter.complete();
            } catch (Exception e) {
                log.error("Validation streaming failed for election {}", electionId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", e.getMessage())));
                } catch (Exception ex) {
                    log.warn("Failed to send SSE error event", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Release election results — anchors final tally to blockchain and
     * transitions the election to RESULTS_RELEASED.
     *
     * @param forceRelease When true, overrides validation warnings (WARN-level issues).
     *                     The UI should prompt for confirmation before setting this.
     */
    @PostMapping("/{electionId}/release")
    public ResponseEntity<?> releaseResults(
            @PathVariable UUID electionId,
            @RequestParam(defaultValue = "false") boolean forceRelease
    ) {
        try {
            AppUser admin = verifyReleaseAccess(electionId);
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
                    "chainTxId", result.chainTxId() != null ? result.chainTxId() : "",
                    "validVotes", result.results().getValidVotes(),
                    "excludedVotes", result.results().getExcludedVoteCount(),
                    "forceReleased", result.results().isForceReleased()
            ));

        } catch (ReleaseService.ReleaseBlockedException e) {
            log.warn("Release blocked for election {}: {}", electionId, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "status", "blocked",
                    "message", e.getMessage(),
                    "validation", e.getValidationReport()
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("status", "forbidden", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "conflict", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to release results for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Integrity summary — quick health stats for the election.
     * Used by the Results tab to show chain anchor status.
     */
    @GetMapping("/{electionId}/integrity-summary")
    public ResponseEntity<?> getIntegritySummary(@PathVariable UUID electionId) {
        try {
            verifyReleaseAccess(electionId);

            long totalVotes = voteRepo.countByElectionId(electionId);
            long anchoredVotes = outboxRepo.countByElectionIdAndAnchoredTrue(electionId);
            long pendingAnchor = outboxRepo.countByElectionIdAndAnchorPendingTrue(electionId);

            var tamperingIncidents = tamperingLogRepo.findByElectionId(electionId);
            long restoredVotes = tamperingIncidents.stream().filter(t -> "RESTORED".equals(t.getIncidentType())).count();
            long unrestorableVotes = tamperingIncidents.stream().filter(t -> "ARCHIVE_TAMPERED".equals(t.getIncidentType())).count();

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
        } catch (com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("status", "forbidden", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get integrity summary for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Verifies the caller has RELEASE_RESULTS permission and belongs to the
     * same organisation as the election (platform admins bypass the org check).
     */
    private AppUser verifyReleaseAccess(UUID electionId) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(actor, PermissionType.RELEASE_RESULTS);
        var election = electionRepository.findById(electionId)
                .orElseThrow(() -> new IllegalArgumentException("Election not found: " + electionId));
        util.requireOrgMatch(actor, election.getOrganization().getOrganizationId());
        return actor;
    }

    private Map<String, Object> sanitizeTamperingLog(TamperingLog t) {
        return Map.of(
                "voteId", t.getVoteId().toString(),
                "incidentType", t.getIncidentType(),
                "detectedAt", t.getDetectedAt().toString(),
                "resolved", t.getResolved()
        );
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("ElectionReleaseController executor shut down");
    }
}
