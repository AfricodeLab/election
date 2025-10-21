package com.brandedhustlers.electionbackend.results.web;

import com.brandedhustlers.electionbackend.results.service.ResultsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/results")
public class ResultsController {
    private final ResultsService service;

    public ResultsController(ResultsService service) {
        this.service = service;
    }

    @GetMapping("/election/{electionId}")
    public ResponseEntity<?> get(@RequestHeader("X-Org-Id") UUID orgId,
                                 @PathVariable UUID electionId) {
        return ResponseEntity.ok(service.get(orgId, electionId));
    }
}