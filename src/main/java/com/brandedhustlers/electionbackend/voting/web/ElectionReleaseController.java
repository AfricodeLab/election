package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.chain.repository.ChainBatchRepository;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.repository.ElectionConfigAnchorRepository;
import com.brandedhustlers.electionbackend.voting.repository.ElectionConfigSnapshotRepository;
import com.brandedhustlers.electionbackend.voting.repository.ElectionResultsAnchorRepository;
import com.brandedhustlers.electionbackend.voting.repository.TamperingLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ElectionReleaseController {
    private final ElectionService electionService;
    private final ReleaseValidationService validationService;
    private final ReleaseService releaseService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ElectionConfigAnchorRepository configAnchorRepo;
    private final ElectionConfigSnapshotRepository snapshotRepo;
    private final ChainBatchRepository batchRepo;
    private final TamperingLogRepository tamperingLogRepo;
    private final ElectionResultsAnchorRepository resultsAnchorRepo;

    public ElectionReleaseController(ElectionService electionService,
                                     ReleaseValidationService validationService,
                                     ReleaseService releaseService, ElectionConfigAnchorRepository configAnchorRepo, ElectionConfigSnapshotRepository snapshotRepo, ChainBatchRepository batchRepo, TamperingLogRepository tamperingLogRepo, ElectionResultsAnchorRepository resultsAnchorRepo) {
        this.electionService = electionService;
        this.validationService = validationService;
        this.releaseService = releaseService;
        this.configAnchorRepo = configAnchorRepo;
        this.snapshotRepo = snapshotRepo;
        this.batchRepo = batchRepo;
        this.tamperingLogRepo = tamperingLogRepo;
        this.resultsAnchorRepo = resultsAnchorRepo;
    }

    /**
     * Stream validation progress (Server-Sent Events).
     * Real-time updates as each check completes.
     */
    @GetMapping(value = "/{electionId}/validate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamValidation(
            @PathVariable UUID electionId,
            @RequestHeader("X-Org-Id") UUID orgId
    ) {
        SseEmitter emitter = new SseEmitter(60000L);

        executor.execute(() -> {
            try {
                // Run validation with progress callbacks
                ReleaseValidationService.ReleaseValidationReport report = validationService.validate(electionId, check -> {
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
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Release election results.
     * Anchors final results to blockchain.
     */
    @PostMapping("/{electionId}/release")
    public ResponseEntity<?> releaseResults(
            @PathVariable UUID electionId,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-Org-Id") UUID orgId,
            @RequestParam(defaultValue = "false") boolean forceRelease
    ) {
        try {
            // Verify admin permissions...

            ReleaseService.ReleaseResult result = releaseService.releaseResults(
                    electionId,
                    adminId,
                    forceRelease
            );

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Results released and anchored",
                    "results", result.results(),
                    "validation", result.validation(),
                    "chainTxId", result.chainTxId()
            ));

        } catch (ReleaseService.ReleaseBlockedException e) {
            return ResponseEntity.status(400).body(Map.of(
                    "status", "blocked",
                    "message", e.getMessage(),
                    "validation", e.getValidationReport()
            ));

        } catch (Exception e) {
            log.error("Failed to release results for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get audit trail for an election.
     */
    @GetMapping("/{electionId}/audit")
    public ResponseEntity<?> getAuditTrail(
            @PathVariable UUID electionId
    ) {
        try {
            // todo verify user org access...

            // Fetch all audit data
            var configAnchor = configAnchorRepo.findById(electionId).orElse(null);
            var snapshots = snapshotRepo.findByElectionIdOrderByCreatedAtAsc(electionId);
            var batches = batchRepo.findByElectionId(electionId);
            var tampering = tamperingLogRepo.findByElectionId(electionId);
            var resultsAnchor = resultsAnchorRepo.findById(electionId).orElse(null);

            return ResponseEntity.ok(Map.of(
                    "electionId", electionId,
                    "configAnchor", configAnchor,
                    "configSnapshots", snapshots,
                    "batchAnchors", batches,
                    "tamperingIncidents", tampering.stream()
                            .map(this::sanitizeTamperingLog)
                            .toList(),
                    "resultsAnchor", resultsAnchor
            ));

        } catch (Exception e) {
            log.error("Failed to get audit trail for election {}", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    private Map<String, Object> sanitizeTamperingLog(TamperingLog log) {
        return Map.of(
                "voteId", log.getVoteId().toString(),
                "incidentType", log.getIncidentType(),
                "detectedAt", log.getDetectedAt().toString(),
                "resolved", log.getResolved()
        );
    }
}
