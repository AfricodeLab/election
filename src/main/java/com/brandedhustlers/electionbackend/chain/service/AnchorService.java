package com.brandedhustlers.electionbackend.chain.service;

import com.brandedhustlers.electionbackend.chain.domain.Batch;
import com.brandedhustlers.electionbackend.chain.repository.ChainBatchRepository;
import com.brandedhustlers.electionbackend.chain.repository.ChainCursorRepository;
import com.brandedhustlers.electionbackend.chain.util.Merkle;
import com.brandedhustlers.electionbackend.common.config.Fabric.ChainClient;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AnchorService {

    private final ElectionRepository electionRepo;
    private final VoteRepository voteRepo;
    private final ChainBatchRepository batchRepo;
    private final ChainCursorRepository cursorRepo;
    private final ChainClient chain;

    public AnchorService(ElectionRepository electionRepo,
                         VoteRepository voteRepo,
                         ChainBatchRepository batchRepo,
                         ChainCursorRepository cursorRepo,
                         ChainClient chain) {
        this.electionRepo = electionRepo;
        this.voteRepo = voteRepo;
        this.batchRepo = batchRepo;
        this.cursorRepo = cursorRepo;
        this.chain = chain;
    }

    @Transactional
    public void anchorElectionWindow(UUID electionId, Instant now) {
        UUID orgId = electionRepo.findOrgId(electionId);
        Instant from = cursorRepo.lastAnchoredAt(electionId).orElse(Instant.EPOCH);
        Instant to = now;

        // Load votes since 'from' (you can cap list size if needed)
        List<UUID> voteIds = voteRepo.findIdsSince(electionId, from);
        if (voteIds.isEmpty()){
            return;
        }

        // Build leaves (v1: voteId only; v2: include candidateId & orgId)
        // For v1:
        List<byte[]> leaves = voteIds.stream()
                .map(id -> sha256(id.toString()))
                .toList();

        String merkleRoot = Merkle.rootHex(leaves);
        String tsIso = to.toString();

        // Submit anchor (idempotent on chain: batch key uses electionId:timestamp)
        String batchIdStr = chain.anchorBatch(
                electionId.toString(), orgId.toString(), merkleRoot, tsIso);

        // Persist local batch
        UUID batchId = tryParseUuidOrRandom(batchIdStr);
        Batch row = Batch.builder()
                .batchId(batchId)
                .electionId(electionId)
                .orgId(orgId)
                .merkleRoot(merkleRoot)
                .fromTs(Timestamp.from(from))
                .toTs(Timestamp.from(to))
                .status("SUBMITTED")
                .createdAt(Timestamp.from(Instant.now()))
                .build();
        batchRepo.save(row);

        // Advance cursor
        cursorRepo.upsert(electionId, to);
        log.info("blockchain service...");

    }

    private static byte[] sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static UUID tryParseUuidOrRandom(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}