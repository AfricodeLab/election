package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.service.VoterImportService;
import com.brandedhustlers.electionbackend.voter.service.VoterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voters")
public class VoterController {
    private final VoterService voterService;
    private final VoterImportService importService;

    public VoterController(VoterService voterService, VoterImportService importService) {
        this.voterService = voterService;
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("X-Org-Id") UUID orgId,
                                           @Valid @RequestBody CreateVoterCmd cmd) {
        return ResponseEntity.ok(voterService.create(orgId, cmd));
    }

    @GetMapping
    public ResponseEntity<?> search(@RequestHeader("X-Org-Id") UUID orgId,
                                                 @RequestParam String q,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(voterService.search(orgId, q, limit));
    }

    @PostMapping("/assign")
    public ResponseEntity<?> assign(@RequestHeader("X-Org-Id") UUID orgId,
                                       @Valid @RequestBody AssignVotersCmd cmd) {
        voterService.assignToElection(orgId, cmd);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/import")
    public ResponseEntity<?> startImport(@RequestHeader("X-Org-Id") UUID orgId,
                                                           @RequestParam String fileRef) {
        UUID jobId = importService.startImport(orgId, fileRef);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/import/{jobId}/status")
    public ResponseEntity<?> status(@RequestHeader("X-Org-Id") UUID orgId,
                                                  @PathVariable UUID jobId) {
        return ResponseEntity.ok(importService.status(orgId, jobId));
    }
}
