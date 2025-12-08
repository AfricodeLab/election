package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Captures election configuration at key moments:
 * - GO_LIVE: When voting starts
 * - CHANGE_DETECTED: When config changes during voting
 *
 * WHY: If a candidate name changes while voting is active, we need
 * to prove what the config was at different points in time. This
 * creates an audit trail of all changes.
 *
 * At release validation, we compare the current config to the GO_LIVE
 * snapshot. Any differences trigger warnings.
 */
@Entity
@Table(name = "election_config_snapshot", indexes = {
        @Index(name = "idx_config_snapshot_election", columnList = "election_id, created_at")
}, schema = "voting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionConfigSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    /**
     * Type of snapshot:
     * - "GO_LIVE": Initial config when voting started
     * - "CHANGE_DETECTED": Config modified during voting
     */
    @Column(name = "snapshot_type", nullable = false, length = 20)
    private String snapshotType;

    /**
     * JSON array of candidates at this moment in time.
     * Example: [{"id":"...","name":"Alice","position":1},...]
     *
     * We serialize the full candidate list so we can show diffs later.
     */
    @Column(name = "candidates_json", nullable = false, columnDefinition = "TEXT")
    private String candidatesJson;

    /**
     * SHA-256 hash of the candidates JSON (for quick comparison).
     * If two snapshots have the same hash, config is identical.
     */
    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Timestamp createdAt = Timestamp.from(Instant.now());

    /**
     * Admin who triggered this change (null for GO_LIVE snapshot).
     */
    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * Human-readable reason for the change.
     * Example: "Candidate name changed: John -> Jonathan"
     */
    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;
}