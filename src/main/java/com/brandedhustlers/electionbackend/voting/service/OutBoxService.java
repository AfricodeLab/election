package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.common.config.messaging.RabbitMQConfig;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.domain.VoteArchive;
import com.brandedhustlers.electionbackend.voting.enums.PublishStatus;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.TamperingLogRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteArchiveRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.brandedhustlers.electionbackend.voting.util.Hash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OutBoxService implements OutBoxRelay {
    private final OutBoxRepository outbox;
    private final RabbitTemplate rabbit;
    private final Hash getHash;
    private final VoteRepository voteRepository;
    private final VoteArchiveRepository voteArchive;
    private final TamperingLogRepository tamperingLog;
    private final AlertService alertService;

    public OutBoxService(OutBoxRepository outbox,
                         RabbitTemplate rabbit,
                         Hash getHash,
                         VoteRepository voteRepository,
                         VoteArchiveRepository voteArchive,
                         TamperingLogRepository tamperingLog, AlertService alertService) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.getHash = getHash;
        this.voteRepository = voteRepository;
        this.voteArchive = voteArchive;
        this.tamperingLog = tamperingLog;
        this.alertService = alertService;
    }

    /** Maximum outbox entries processed per scheduler tick. */
    private static final int BATCH_SIZE = 200;

    @Override
    @Transactional
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void publishPending() {
        // Limit to BATCH_SIZE per tick — prevents loading thousands of rows
        // during a high-volume election and keeps each scheduler cycle fast.
        List<OutBox> batch = outbox.findPendingBatch(PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) {
            return; // nothing to do — skip the rest of the method
        }

        // Batch-fetch all votes in ONE query instead of one findById per entry.
        // Eliminates the N+1 pattern: 200 outbox entries → 1 SELECT IN (...).
        List<UUID> voteIds = batch.stream()
                .map(OutBox::getAggregateId)
                .collect(Collectors.toList());

        Map<UUID, Vote> voteMap = voteRepository.findAllById(voteIds).stream()
                .collect(Collectors.toMap(Vote::getId, Function.identity()));

        // Batch-fetch archives in ONE query as well (only needed when tampering is detected,
        // but fetching up-front avoids a second round-trip inside the loop in the common path).
        Map<UUID, VoteArchive> archiveMap = voteArchive.findAllById(voteIds).stream()
                .collect(Collectors.toMap(VoteArchive::getVoteId, Function.identity()));

        /**
         * Track which entries were successfully validated.
         * These will be marked as anchor_pending=true at the end.
         */
        List<OutBox> validatedEntries = new ArrayList<>();

        /**
         * All outbox entries whose state changed this tick — flushed in one saveAll
         * after the loop instead of one save() per entry.
         */
        List<OutBox> modifiedOutboxEntries = new ArrayList<>();

        /**
         * Track tampering incidents for alerting.
         */
        List<TamperingLog> incidents = new ArrayList<>();

        for (OutBox o : batch) {
            try {
                log.info("Processing outbox entry {} for vote {}", o.getId(), o.getAggregateId());

                // Step 1: Look up the vote from the pre-fetched map (no DB round-trip)
                Vote tableVote = voteMap.get(o.getAggregateId());
                if (tableVote == null) {
                    throw new IllegalArgumentException("vote not found: " + o.getAggregateId());
                }

                // Step 2: Recompute hash from current table data
                String currentVoteHash = getHash.hashVote(tableVote);

                // Step 3: Compare to hash from cast time (stored in outbox)
                if (!o.getVoteHash().equals(currentVoteHash)) {
                    /**
                     * TAMPERING DETECTED!
                     * The vote in the table doesn't match what was cast.
                     *
                     * Now we attempt restoration from archive.
                     */
                    log.warn("TAMPERING DETECTED: Vote {} hash mismatch", o.getAggregateId());
                    log.warn("Expected hash: {}", o.getVoteHash());
                    log.warn("Current hash:  {}", currentVoteHash);

                    // Step 4: Look up archive from pre-fetched map (no DB round-trip)
                    VoteArchive archived = archiveMap.get(o.getAggregateId());
                    if (archived == null) {
                        throw new IllegalStateException("Archive missing for vote: " + o.getAggregateId());
                    }

                    // Step 5: Verify archive integrity
                    /**
                     * We rebuild the vote from archive fields and hash it.
                     * If archive hash matches original outbox hash, archive is clean.
                     */
                    Vote archivedVoteForHashing = Vote.builder()
                            .id(archived.getVoteId())
                            .electionId(archived.getElectionId())
                            .candidateId(archived.getCandidateId())
                            .voterId(archived.getVoterId())
                            .orgId(archived.getOrgId())
                            .dedupeKey(archived.getDedupeKey())
                            .castAt(archived.getCastAt().toInstant())
                            .build();

                    String archivedHash = getHash.hashVote(archivedVoteForHashing);

                    if (!o.getVoteHash().equals(archivedHash)) {
                        /**
                         * CRITICAL: Archive also tampered!
                         * This vote is permanently lost.
                         */
                        log.error("CRITICAL: Archive tampered for vote {}", o.getAggregateId());
                        log.error("Original hash: {}", o.getVoteHash());
                        log.error("Archive hash:  {}", archivedHash);
                        log.error("Current hash:  {}", currentVoteHash);

                        // Mark outbox as failed — collected for batch save below
                        o.setPublishStatus(PublishStatus.FAILED_VALIDATION);
                        o.setFailedValidationAt(Timestamp.from(Instant.now()));
                        modifiedOutboxEntries.add(o);

                        // Log critical incident
                        TamperingLog incident = TamperingLog.builder()
                                .voteId(o.getAggregateId())
                                .electionId(tableVote.getElectionId())
                                .orgId(tableVote.getOrgId())
                                .incidentType("ARCHIVE_TAMPERED")
                                .originalHash(o.getVoteHash())
                                .tamperedHash(currentVoteHash)
                                .archiveHash(archivedHash)
                                .resolved(false)  // Cannot be restored
                                .build();
                        incidents.add(incident);

                        continue; // Skip this vote, it's lost
                    }

                    /**
                     * Archive is clean! Restore the vote.
                     */
                    log.info("Restoring vote {} from archive", o.getAggregateId());

                    Vote restored = Vote.builder()
                            .id(archived.getVoteId())
                            .electionId(archived.getElectionId())
                            .candidateId(archived.getCandidateId())
                            .voterId(archived.getVoterId())
                            .orgId(archived.getOrgId())
                            .dedupeKey(archived.getDedupeKey())
                            .castAt(archived.getCastAt().toInstant())
                            // Fields not in archive remain null (deviceId, proofRef, txId)
                            .build();

                    voteRepository.save(restored);
                    log.info("Vote {} restored successfully", o.getAggregateId());

                    // Log restoration incident
                    TamperingLog incident = TamperingLog.builder()
                            .voteId(o.getAggregateId())
                            .electionId(tableVote.getElectionId())
                            .orgId(tableVote.getOrgId())
                            .incidentType("RESTORED")
                            .originalHash(o.getVoteHash())
                            .tamperedHash(currentVoteHash)
                            .archiveHash(archivedHash)
                            .resolved(true)  // Successfully restored
                            .build();
                    incidents.add(incident);

                    // Now the vote is clean, proceed to publish
                }

                /**
                 * Step 6: Publish to RabbitMQ (projector)
                 * At this point, the vote is guaranteed to be clean
                 * (either was never tampered, or was restored).
                 */
                rabbit.convertAndSend(RabbitMQConfig.VOTING_VOTE_CAST, o.getPayload());
                o.setPublishedAt(Timestamp.from(Instant.now()));
                o.setFailedValidationAt(null);
                o.setPublishStatus(PublishStatus.SUCCESS);

                /**
                 * Mark as pending anchor.
                 * The anchor scheduler will pick this up and include it
                 * in the next 30-second batch.
                 */
                o.setAnchorPending(true);
                modifiedOutboxEntries.add(o);
                validatedEntries.add(o);

                log.info("Successfully published vote {} from outbox entry {}",
                        o.getAggregateId(), o.getId());

            } catch (Exception e) {
                log.error("Failed to publish outbox entry {} for vote {}",
                        o.getId(), o.getAggregateId(), e);
            }
        }

        // Flush all outbox state changes in one batch round-trip.
        if (!modifiedOutboxEntries.isEmpty()) {
            outbox.saveAll(modifiedOutboxEntries);
        }

        /**
         * Step 7: Persist tampering incidents and alert admins
         */
        if (!incidents.isEmpty()) {
            tamperingLog.saveAll(incidents);

            // Count critical vs resolved
            long critical = incidents.stream()
                    .filter(i -> "ARCHIVE_TAMPERED".equals(i.getIncidentType()))
                    .count();
            long restored = incidents.stream()
                    .filter(i -> "RESTORED".equals(i.getIncidentType()))
                    .count();

            log.warn("Tampering summary: {} restored, {} critical", restored, critical);

            // Alert org admins
            alertService.notifyTamperingDetected(incidents);
        }

        log.info("Outbox processing complete: {} validated, {} incidents",
                validatedEntries.size(), incidents.size());
    }
}