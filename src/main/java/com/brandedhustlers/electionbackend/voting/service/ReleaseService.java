package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.chain.service.BlockchainProvider;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voting.domain.ElectionResultsAnchor;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.repository.ElectionResultsAnchorRepository;
import com.brandedhustlers.electionbackend.voting.repository.TamperingLogRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReleaseService {

    private final ElectionRepository electionRepo;
    private final VoteRepository voteRepo;
    private final TamperingLogRepository tamperingLog;
    private final ElectionResultsAnchorRepository resultsAnchorRepo;
    private final ReleaseValidationService validationService;
    private final BlockchainProvider chain;
    private final ObjectMapper objectMapper;
    private final AuditEmitter emitter;
    private final AppUserRepository appUserRepository;

    public ReleaseService(
            ElectionRepository electionRepo,
            VoteRepository voteRepo,
            TamperingLogRepository tamperingLog,
            ElectionResultsAnchorRepository resultsAnchorRepo,
            ReleaseValidationService validationService,
            BlockchainProvider chain,
            ObjectMapper objectMapper, AuditEmitter emitter, AppUserRepository appUserRepository) {
        this.electionRepo = electionRepo;
        this.voteRepo = voteRepo;
        this.tamperingLog = tamperingLog;
        this.resultsAnchorRepo = resultsAnchorRepo;
        this.validationService = validationService;
        this.chain = chain;
        this.objectMapper = objectMapper;
        this.emitter = emitter;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Release election results after validation.
     *
     * @param electionId   The election
     * @param adminUserId  Admin releasing the results
     * @param forceRelease True to override validation warnings/errors
     * @return Release result with anchored data
     */
    @Transactional
    public ReleaseResult releaseResults(UUID electionId, UUID adminUserId, boolean forceRelease) {

        log.info("Starting release for election {}, admin={}, force={}",
                electionId, adminUserId, forceRelease);

        /**
         * Step 1: Run validation.
         */
        ReleaseValidationService.ReleaseValidationReport validation = validationService.validate(electionId, null);

        if (validation.isAutoReleaseBlocked() && !forceRelease) {
            throw new ReleaseBlockedException(
                    "Release blocked due to validation errors. Use forceRelease=true to override.",
                    validation);
        }

        /**
         * Step 2: Get excluded votes (unrestorable tampering).
         */
        List<UUID> excludedVoteIds = tamperingLog
                .findByElectionIdAndIncidentType(electionId, "ARCHIVE_TAMPERED")
                .stream()
                .map(TamperingLog::getVoteId)
                .toList();

        /**
         * Step 3: Compute final tally (excluding bad votes).
         */
        Map<UUID, Long> tally = computeTally(electionId, excludedVoteIds);

        /**
         * Step 4: Build results object.
         */
        ElectionResults results = ElectionResults.builder()
                .electionId(electionId)
                .orgId(electionRepo.findOrgId(electionId))
                .totalVotesCast(voteRepo.countByElectionId(electionId))
                .validVotes(tally.values().stream().mapToLong(Long::longValue).sum())
                .excludedVoteCount(excludedVoteIds.size())
                .tally(tally)
                .validationReport(validation)
                .releasedAt(Instant.now())
                .releasedBy(adminUserId)
                .forceReleased(forceRelease)
                .build();

        String resultsJson = serializeResults(results);
        String resultsHash = sha256Hex(resultsJson);

        log.info("Computed results for election {}: {} valid votes, {} excluded, hash={}",
                electionId, results.getValidVotes(), results.getExcludedVoteCount(), resultsHash);

        /**
         * Step 5: Anchor to blockchain.
         */
        String txId = chain.anchorResults(
                electionId.toString(),
                results.getOrgId().toString(),
                resultsHash,
                results.getExcludedVoteCount(),
                forceRelease,
                adminUserId.toString(),
                Instant.now().toString());

        /**
         * Step 6: Store anchor record locally.
         */
        ElectionResultsAnchor anchor = ElectionResultsAnchor.builder()
                .electionId(electionId)
                .resultsJson(resultsJson)
                .resultsHash(resultsHash)
                .excludedVoteCount(excludedVoteIds.size())
                .chainTxId(txId)
                .releasedAt(Timestamp.from(Instant.now()))
                .releasedBy(adminUserId)
                .forceReleased(forceRelease)
                .build();

        resultsAnchorRepo.save(anchor);

        /**
         * Step 7: Update election status.
         */
        var election = electionRepo.findById(electionId).orElseThrow();
        var adminUser = appUserRepository.findById(adminUserId);
        election.setElectionStatus(ElectionStatus.RESULTS_RELEASED);
        electionRepo.save(election);

        log.info("Results released for election {}, txId={}", electionId, txId);

        emitter.emit(UUID.randomUUID(), "auth.register.duplicate", null, adminUser.get().getActiveOrganization().getOrganizationId(),
                forceRelease ? "Results force released for election." : "Results released for election.",
                adminUserId,adminUser.get().getFirstName(),adminUser.get().getUserRole().name(), "USER",
                "Auth", adminUserId, null, "Auth", null, null, null, "INFO", 1);

        return new ReleaseResult(results, validation, txId);
    }

    /**
     * Compute tally excluding specific votes.
     */
    private Map<UUID, Long> computeTally(UUID electionId, List<UUID> excludedVoteIds) {
        // Get all votes for this election
        List<Vote> allVotes = voteRepo.findByElectionId(electionId);

        // Filter out excluded votes
        return allVotes.stream()
                .filter(v -> !excludedVoteIds.contains(v.getId()))
                .collect(Collectors.groupingBy(
                        Vote::getCandidateId,
                        Collectors.counting()));
    }

    private String serializeResults(ElectionResults results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize results", e);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }

    /**
     * Exception thrown when release is blocked.
     */
    public static class ReleaseBlockedException extends RuntimeException {
        private final ReleaseValidationService.ReleaseValidationReport validationReport;

        public ReleaseBlockedException(String message, ReleaseValidationService.ReleaseValidationReport report) {
            super(message);
            this.validationReport = report;
        }

        public ReleaseValidationService.ReleaseValidationReport getValidationReport() {
            return validationReport;
        }
    }

    /**
     * Results object (stored in DB and anchored to chain).
     */
    @Data
    @Builder
    public static class ElectionResults {
        private UUID electionId;
        private UUID orgId;
        private long totalVotesCast;
        private long validVotes;
        private int excludedVoteCount;
        private Map<UUID, Long> tally; // candidateId → vote count
        private ReleaseValidationService.ReleaseValidationReport validationReport;
        private Instant releasedAt;
        private UUID releasedBy;
        private boolean forceReleased;
    }

    /**
     * Release result returned to caller.
     */
    public record ReleaseResult(
            ElectionResults results,
            ReleaseValidationService.ReleaseValidationReport validation,
            String chainTxId) {
    }
}