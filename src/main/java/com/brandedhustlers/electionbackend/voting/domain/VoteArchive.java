package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable archive of vote data at the moment of casting.
 * This serves as the "source of truth" for vote integrity validation.
 *
 * WHY: When outbox validation detects tampering in the votes table,
 * we compare against this archive to restore the original vote.
 *
 * SECURITY: Contains the salted hash computed at cast time. Even if
 * an attacker modifies the votes table, they can't recompute valid
 * hashes without knowing the salt (stored in Hash service).
 */
@Entity
@Table(name = "vote_archive", indexes = {
        @Index(name = "idx_vote_archive_election", columnList = "election_id"),
        @Index(name = "idx_vote_archive_hash", columnList = "vote_hash")
}, schema = "voting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteArchive {

    @Id
    @Column(name = "vote_id", nullable = false)
    private UUID voteId;  // Same as Vote.id - links the two

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "dedupe_key")
    private String dedupeKey;

    @Column(name = "cast_at", nullable = false)
    private Timestamp castAt;

    /**
     * Salted hash of the vote at cast time.
     * Format: SHA-256(vote fields + salt)
     * This is our "ground truth" for detecting tampering.
     */
    @Column(name = "vote_hash", nullable = false, length = 64)
    private String voteHash;

    /**
     * When this archive entry was created (same as cast time, but explicit).
     */
    @Column(name = "archived_at", nullable = false)
    @Builder.Default
    private Timestamp archivedAt = Timestamp.from(Instant.now());
}