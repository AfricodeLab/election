package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Logs every detected tampering incident.
 *
 * WHY: When outbox validation finds a hash mismatch, we need to:
 * 1. Record what happened
 * 2. Track whether we restored it
 * 3. Provide audit trail for release validation
 *
 * INCIDENT TYPES:
 * - "RESTORED": Vote was tampered but we recovered from archive
 * - "ARCHIVE_TAMPERED": Both vote and archive were tampered (critical!)
 */
@Entity
@Table(name = "tampering_log", indexes = {
        @Index(name = "idx_tampering_election", columnList = "election_id"),
        @Index(name = "idx_tampering_detected", columnList = "detected_at"),
        @Index(name = "idx_tampering_vote", columnList = "vote_id")
}, schema = "voting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TamperingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "vote_id", nullable = false)
    private UUID voteId;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    /**
     * What happened:
     * - "RESTORED": Successfully recovered from archive
     * - "ARCHIVE_TAMPERED": Archive also corrupt, vote lost
     */
    @Column(name = "incident_type", nullable = false, length = 50)
    private String incidentType;

    /**
     * The expected hash (from outbox at cast time).
     */
    @Column(name = "original_hash", nullable = false, length = 64)
    private String originalHash;

    /**
     * The hash we found in the votes table (proof of tampering).
     */
    @Column(name = "tampered_hash", length = 64)
    private String tamperedHash;

    /**
     * The hash from vote_archive (should match original_hash if archive is clean).
     */
    @Column(name = "archive_hash", length = 64)
    private String archiveHash;

    @Column(name = "detected_at", nullable = false)
    @Builder.Default
    private Timestamp detectedAt = Timestamp.from(Instant.now());

    /**
     * False if vote is still lost/corrupted.
     * True if we successfully restored or admin acknowledged.
     */
    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private Boolean resolved = false;
}
