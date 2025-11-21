package com.brandedhustlers.electionbackend.common.config.Fabric;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/chain")
public class ChainTestController {
    private final ChainClient chain;

    public ChainTestController(ChainClient chain) {
        this.chain = chain;
    }

    @PostMapping("/anchor")
    public Map<String, Object> anchor(@RequestParam String electionId, @RequestParam String orgId) {
        var batchId = chain.anchorBatch(electionId, orgId, "ROOT_FROM_APP", java.time.Instant.now().toString());
        return Map.of("batchId", batchId);
    }

    @GetMapping("/latest")
    public Map<String, Object> latest(@RequestParam String electionId) {
        return Map.of("latest", Objects.requireNonNull(chain.getLatest(electionId).orElse(null)));
    }
}
