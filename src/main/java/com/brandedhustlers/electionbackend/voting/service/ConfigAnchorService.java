/*
package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.chain.service.BlockchainService;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.domain.ElectionConfigAnchor;
import com.brandedhustlers.electionbackend.voting.dto.ElectionConfigDTO;
import com.brandedhustlers.electionbackend.voting.repository.ElectionConfigAnchorRepository;
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
public class ConfigAnchorService {

    private final ElectionRepository electionRepo;
    private final CandidateRepository candidateRepo;
    private final ElectionConfigAnchorRepository configAnchorRepo;
    private final BlockchainService chain;  // Your existing blockchain service
    private final ObjectMapper objectMapper;

    public ConfigAnchorService(
            ElectionRepository electionRepo,
            CandidateRepository candidateRepo,
            ElectionConfigAnchorRepository configAnchorRepo,
            BlockchainService chain,
            ObjectMapper objectMapper
    ) {
        this.electionRepo = electionRepo;
        this.candidateRepo = candidateRepo;
        this.configAnchorRepo = configAnchorRepo;
        this.chain = chain;
        this.objectMapper = objectMapper;
    }

    */
/**
     * Anchor election config to blockchain.
     * Called when election transitions to LIVE.
     *
     * @param electionId The election to anchor
     * @return The config anchor record
     *//*

    @Transactional
    public ElectionConfigAnchor anchorConfig(UUID electionId) {
        try {
            */
/**
             * Step 1: Build canonical config representation.
             * Include only immutable election properties:
             * - Election ID, title, times
             * - Candidate list (id, name, position)
             * - Eligibility rules (if any)
             *//*

            if (configAnchorRepo.existsById(electionId)) {
                log.warn("Config already anchored for election {}", electionId);
                return configAnchorRepo.findById(electionId).orElseThrow();
            }

            Election election = electionRepo.findById(electionId)
                    .orElseThrow(() -> new IllegalArgumentException("Election not found"));

            List<Candidate> candidates = candidateRepo.findByElectionElectionId(electionId);

            */
/**
             * Build canonical config DTO.
             *//*

            ElectionConfigDTO config = ElectionConfigDTO.builder()
                    .electionId(election.getElectionId())
                    .orgId(election.getOrganization().getOrganizationId())
                    .title(election.getElectionTitle())
                    .startTime(election.getElectionStartTime())
                    .endTime(election.getElectionEndTime())
                    .candidates(candidates.stream()
                            .map(c -> new ElectionConfigDTO.CandidateInfo(
                                    c.getCandidateId(),
                                    c.getCandidateName(),
                                    c.getCandidatePosition()
                            ))
                            .toList())
                    .eligibilityPolicy(election.getEligibilityPolicy())
                    .policyVersion(election.getPolicyVersion())
                    .build();

            String configJson = objectMapper.writeValueAsString(config);
            String configHash = sha256Hex(configJson);

            log.info("Anchoring config for election {}, hash: {}", electionId, configHash);

            */
/**
             * REAL BLOCKCHAIN CALL HERE.
             * Submit to ConfigContract:AnchorConfig.
             *//*

            String txId = chain.anchorConfig(
                    election.getElectionId().toString(),
                    election.getOrganization().getOrganizationId().toString(),
                    configHash,
                    Instant.now().toString()
            );

            */
/**
             * Store anchor record locally.
             *//*

            ElectionConfigAnchor anchor = ElectionConfigAnchor.builder()
                    .electionId(electionId)
                    .configJson(configJson)
                    .configHash(configHash)
                    .chainTxId(txId)
                    .anchoredAt(Timestamp.from(Instant.now()))
                    .build();

            configAnchorRepo.save(anchor);

            log.info("Config anchored for election {}, txId: {}", electionId, txId);

            return anchor;

        } catch (Exception e) {
            log.error("Failed to anchor config for election {}", electionId, e);
            throw new RuntimeException("Config anchoring failed", e);
        }
    }

    */
/**
     * Verify if current config matches anchored config.
     * Returns null if match, or a diff report if mismatch.
     *//*

    @Transactional(readOnly = true)
    public ConfigValidationResult validateConfig(UUID electionId) {
        try {
            // Get anchored config
            ElectionConfigAnchor anchor = configAnchorRepo.findById(electionId)
                    .orElseThrow(() -> new IllegalStateException("No config anchor found"));

            // Build current config
            Election election = electionRepo.findById(electionId).orElseThrow();
            List<Candidate> candidates = candidateRepo.findByElectionElectionId(electionId);

            ElectionConfigDTO currentConfig = ElectionConfigDTO.builder()
                    .electionId(election.getElectionId())
                    .orgId(election.getOrganization().getOrganizationId())
                    .title(election.getElectionTitle())
                    .startTime(election.getElectionStartTime())
                    .endTime(election.getElectionEndTime())
                    .candidates(candidates.stream()
                            .map(c -> new ElectionConfigDTO.CandidateInfo(
                                    c.getCandidateId(),
                                    c.getCandidateName(),
                                    c.getCandidatePosition()
                            ))
                            .toList())
                    .build();

            String currentJson = objectMapper.writeValueAsString(currentConfig);
            String currentHash = sha256Hex(currentJson);

            // Compare hashes
            if (anchor.getConfigHash().equals(currentHash)) {
                return new ConfigValidationResult(true, null, null);
            } else {
                // Config changed - build diff
                ElectionConfigDTO anchoredConfig = objectMapper.readValue(
                        anchor.getConfigJson(),
                        ElectionConfigDTO.class
                );

                return new ConfigValidationResult(
                        false,
                        anchoredConfig,
                        currentConfig
                );
            }

        } catch (Exception e) {
            log.error("Failed to validate config for election {}", electionId, e);
            throw new RuntimeException("Config validation failed", e);
        }
    }

    */
/**
     * Result of config validation.
     *//*

    public record ConfigValidationResult(
            boolean matches,
            ElectionConfigDTO anchoredConfig,
            ElectionConfigDTO currentConfig
    ) {}

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
*/
