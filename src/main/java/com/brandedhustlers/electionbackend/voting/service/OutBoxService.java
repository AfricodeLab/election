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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    @Transactional
    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void publishPending() {
        List<OutBox> batch = outbox.findTopByPublishedAtIsNull();

        /**
         * Track which entries were successfully validated.
         * These will be marked as anchor_pending=true at the end.
         */
        List<OutBox> validatedEntries = new ArrayList<>();

        /**
         * Track tampering incidents for alerting.
         */
        List<TamperingLog> incidents = new ArrayList<>();

        for (OutBox o : batch) {
            try {
                log.info("Processing outbox entry {} for vote {}", o.getId(), o.getAggregateId());

                // Step 1: Fetch the vote from the working table
                Vote tableVote = voteRepository.findById(o.getAggregateId())
                        .orElseThrow(() -> new IllegalArgumentException("vote not found: " + o.getAggregateId()));

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

                    // Step 4: Try to restore from archive
                    VoteArchive archived = voteArchive.findById(o.getAggregateId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Archive missing for vote: " + o.getAggregateId()));

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

                        // Mark outbox as failed
                        o.setPublishStatus(PublishStatus.FAILED_VALIDATION);
                        o.setFailedValidationAt(Timestamp.from(Instant.now()));
                        outbox.save(o);

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
                outbox.save(o);

                validatedEntries.add(o);

                log.info("Successfully published vote {} from outbox entry {}",
                        o.getAggregateId(), o.getId());

            } catch (Exception e) {
                log.error("Failed to publish outbox entry {} for vote {}",
                        o.getId(), o.getAggregateId(), e);
            }
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