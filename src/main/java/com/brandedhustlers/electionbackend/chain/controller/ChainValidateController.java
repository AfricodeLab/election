package com.brandedhustlers.electionbackend.chain.controller;

import com.brandedhustlers.electionbackend.chain.util.Merkle;
import com.brandedhustlers.electionbackend.chain.service.BlockchainProvider;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.RequiresFeature;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chain")
public class ChainValidateController {

    private final VoteRepository voteRepo;
    private final BlockchainProvider chain;

    public ChainValidateController(VoteRepository voteRepo, BlockchainProvider chain) {
        this.voteRepo = voteRepo;
        this.chain = chain;
    }

    @RequiresFeature(Feature.MERKLE_VERIFICATION)
    @GetMapping("/validate")
    public Map<String, Object> validate(
            @RequestParam UUID electionId,
            @RequestParam Instant fromTs,
            @RequestParam Instant toTs) throws Exception {

        // recompute local root
        var voteIds = voteRepo.findIdsBetween(electionId, fromTs, toTs); // implement
        var leaves = voteIds.stream().map(id -> sha256(id.toString())).toList();
        var localRoot = Merkle.rootHex(leaves);

        // get on-chain latest (or extend chaincode to fetch batch by window)
        var latest = chain.getLatestBatch(electionId.toString());

        var out = new LinkedHashMap<String, Object>();
        out.put("electionId", electionId.toString());
        out.put("fromTs", fromTs);
        out.put("toTs", toTs);
        out.put("localRoot", localRoot);

        if (latest != null) {
            var node = new ObjectMapper().readTree(latest);
            String anchoredRoot = node.path("merkleRoot").asText();
            out.put("anchoredRoot", anchoredRoot);
            out.put("anchoredAt", node.path("anchoredAt").asText());
            out.put("match", localRoot.equalsIgnoreCase(anchoredRoot));
        } else {
            out.put("anchoredRoot", null);
            out.put("anchoredAt", null);
            out.put("match", false);
        }
        return out;
    }

    private static byte[] sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
