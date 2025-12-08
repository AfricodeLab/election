/*
package com.brandedhustlers.electionbackend.chain.service;

import com.brandedhustlers.electionbackend.chain.domain.Batch;
import com.brandedhustlers.electionbackend.chain.repository.ChainBatchRepository;
import com.brandedhustlers.electionbackend.chain.util.Merkle;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
public class AnchorService {

    private final ElectionRepository electionRepo;
    private final OutBoxRepository outbox;  // Changed from VoteRepository
    private final ChainBatchRepository batchRepo;
    private final BlockchainService chain;

    public AnchorService(
            ElectionRepository electionRepo,
            OutBoxRepository outbox,
            ChainBatchRepository batchRepo,
            BlockchainService chain
    ) {
        this.electionRepo = electionRepo;
        this.outbox = outbox;
        this.batchRepo = batchRepo;
        this.chain = chain;
    }

    */
/**
     * Run every 30 seconds (changed from 10s for cost efficiency).
     * Anchors all validated votes that are pending anchor.
     *//*

//    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void anchorPendingVotes() {
        Timestamp now = Timestamp.from(Instant.now());

        */
/**
         * Fetch all outbox entries where:
         * - anchor_pending = true (validated and ready to anchor)
         * - anchored = false (not yet included in a batch)
         *//*

        List<OutBox> pendingEntries = outbox.findByAnchorPendingTrueAndAnchoredFalse();

        if (pendingEntries.isEmpty()) {
            log.debug("No pending votes to anchor");
            return;
        }

        log.info("Found {} votes pending anchor", pendingEntries.size());

        */
/**
         * Group by election ID.
         * We create one batch per election per cycle.
         *//*

        Map<UUID, List<OutBox>> byElection = pendingEntries.stream()
                .collect(Collectors.groupingBy(o -> {
                    // Extract electionId from the vote (via aggregateId)
                    // We need to parse the payload or join with votes table
                    return extractElectionId(o);
                }));

        */
/**
         * Process each election's batch.
         *//*

        byElection.forEach((electionId, entries) -> {
            try {
                anchorElectionBatch(electionId, entries, now);
            } catch (Exception e) {
                log.error("Failed to anchor batch for election {}", electionId, e);
                // Don't throw - let other elections proceed
            }
        });
    }

    */
/**
     * Anchor a single batch for one election.
     *
     * @param electionId The election
     * @param entries Outbox entries to include in this batch
     * @param now Current timestamp
     *//*

    private void anchorElectionBatch(UUID electionId, List<OutBox> entries, Timestamp now) {
        UUID orgId = electionRepo.findOrgId(electionId);

        */
/**
         * CRITICAL CHANGE:
         * Build Merkle tree from OUTBOX HASHES, not from re-reading votes table.
         *
         * WHY: The outbox hash was computed at cast time, before any tampering.
         * If we read from votes table, we might anchor tampered data.
         *
         * By using outbox hashes, we anchor only votes that:
         * 1. Were cast originally
         * 2. Passed validation (or were restored)
         * 3. Are guaranteed to be in their original state
         *//*

        List<byte[]> leaves = entries.stream()
                .map(o -> hexToBytes(o.getVoteHash()))
                .toList();

        if (leaves.isEmpty()) {
            log.warn("No valid leaves for election {}", electionId);
            return;
        }

        String merkleRoot = Merkle.rootHex(leaves);

        log.info("Anchoring batch for election {}: {} votes, root: {}",
                electionId, entries.size(), merkleRoot);

        */
/**
         * Submit to blockchain.
         * This calls AnchorContract:AnchorBatch (your existing method).
         *//*

        String batchIdStr = chain.anchorBatch(
                electionId.toString(),
                orgId.toString(),
                merkleRoot,
                now.toInstant().toString()
        );

        UUID batchId = tryParseUuidOrRandom(batchIdStr);

        Timestamp earliestVote = entries.stream()
                .map(OutBox::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(now);

        Batch batch = Batch.builder()
                .batchId(batchId)
                .electionId(electionId)
                .orgId(orgId)
                .merkleRoot(merkleRoot)
                .voteCount(entries.size())  // NEW field
                .fromTs(earliestVote)
                .toTs(now)
                .status("ANCHORED")
                .chainTxId(batchIdStr)
                .createdAt(now)
                .build();

        batchRepo.save(batch);

        */
/**
         * Mark all outbox entries as anchored.
         *//*

        entries.forEach(o -> {
            o.setAnchorPending(false);
            o.setAnchored(true);
            o.setBatchId(batchId);
            o.setAnchoredAt(now);
            outbox.save(o);
        });

        log.info("Anchored batch {} for election {}: {} votes",
                batchId, electionId, entries.size());
    }

    */
/**
     * Extract election ID from outbox payload.
     * Your payload format: {"id":"...","electionId":"...","candidateId":"...","orgId":"..."}
     *//*

    private UUID extractElectionId(OutBox outboxEntry) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, String> payload = mapper.readValue(
                    outboxEntry.getPayload(),
                    Map.class
            );
            return UUID.fromString(payload.get("electionId"));
        } catch (Exception e) {
            log.error("Failed to extract electionId from outbox {}", outboxEntry.getId(), e);
            throw new RuntimeException("Invalid outbox payload", e);
        }
    }

    */
/**
     * Convert hex string to byte array.
     *//*

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private UUID tryParseUuidOrRandom(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}*/
