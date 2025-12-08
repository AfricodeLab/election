package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.voting.domain.ElectionConfigSnapshot;
import com.brandedhustlers.electionbackend.voting.repository.ElectionConfigSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ConfigSnapshotService {

    private final ElectionConfigSnapshotRepository snapshotRepo;
    private final CandidateRepository candidateRepo;
    private final ObjectMapper objectMapper;

    public ConfigSnapshotService(
            ElectionConfigSnapshotRepository snapshotRepo,
            CandidateRepository candidateRepo,
            ObjectMapper objectMapper
    ) {
        this.snapshotRepo = snapshotRepo;
        this.candidateRepo = candidateRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Capture election config snapshot at a specific moment.
     *
     * @param electionId The election to snapshot
     * @param snapshotType "GO_LIVE" or "CHANGE_DETECTED"
     * @param adminId Who triggered this (null for GO_LIVE)
     * @param reason Why this snapshot was taken
     */
    @Transactional
    public ElectionConfigSnapshot captureSnapshot(
            UUID electionId,
            String snapshotType,
            UUID adminId,
            String reason
    ) {
        try {
            /**
             * Fetch current candidates for this election.
             * We serialize them to JSON for storage.
             */
            List<Candidate> candidates = candidateRepo.findByElectionElectionId(electionId);

            /**
             * Build a simplified representation for hashing.
             * Only include fields that matter for integrity:
             * - candidateId
             * - name (this is what might change)
             * - position
             *
             * We exclude audit fields like createdAt, updatedAt.
             */
            List<CandidateSnapshot> candidateSnapshots = candidates.stream()
                    .map(c -> new CandidateSnapshot(
                            c.getCandidateId(),
                            c.getCandidateName(),
                            c.getCandidatePosition()
                    ))
                    .toList();

            String candidatesJson = objectMapper.writeValueAsString(candidateSnapshots);
            String configHash = sha256Hex(candidatesJson);

            ElectionConfigSnapshot snapshot = ElectionConfigSnapshot.builder()
                    .electionId(electionId)
                    .snapshotType(snapshotType)
                    .candidatesJson(candidatesJson)
                    .configHash(configHash)
                    .createdBy(adminId)
                    .changeReason(reason)
                    .createdAt(Timestamp.from(Instant.now()))
                    .build();

            snapshotRepo.save(snapshot);

            log.info("Captured {} config snapshot for election {} with hash {}",
                    snapshotType, electionId, configHash);

            return snapshot;

        } catch (Exception e) {
            log.error("Failed to capture config snapshot for election {}", electionId, e);
            throw new RuntimeException("Config snapshot failed", e);
        }
    }

    /**
     * Simple DTO for candidate data in snapshots.
     * Only contains fields we care about for integrity checking.
     */
    public record CandidateSnapshot(UUID candidateId, String name, Integer position) {}

    /**
     * Compute SHA-256 hash of a string.
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }
}