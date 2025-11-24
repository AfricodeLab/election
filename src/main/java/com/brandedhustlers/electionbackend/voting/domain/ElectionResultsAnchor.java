package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Stores the blockchain anchor for final election results.
 *
 * WHY: After validation passes (or admin force-releases), we anchor
 * the final results including:
 * - Tally per candidate
 * - Excluded vote count (if any tampering was detected)
 * - Validation report summary
 *
 * This creates cryptographic proof of "these were the official results."
 */
@Entity
@Table(name = "election_results_anchor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionResultsAnchor {

    @Id
    @Column(name = "election_id", nullable = false)
    private UUID electionId;  // One results anchor per election

    /**
     * Full results JSON stored locally.
     * Contains: tally, excludedVotes, tamperingIncidents, validationReport, etc.
     */
    @Column(name = "results_json", nullable = false, columnDefinition = "TEXT")
    private String resultsJson;

    /**
     * SHA-256 hash of results_json (this goes on chain).
     */
    @Column(name = "results_hash", nullable = false, length = 64)
    private String resultsHash;

    /**
     * Number of votes excluded due to unrestorable tampering.
     */
    @Column(name = "excluded_vote_count", nullable = false)
    @Builder.Default
    private Integer excludedVoteCount = 0;

    /**
     * Blockchain transaction ID from the anchor operation.
     */
    @Column(name = "chain_tx_id")
    private String chainTxId;

    @Column(name = "released_at", nullable = false)
    private Timestamp releasedAt;

    /**
     * Admin who released the results.
     */
    @Column(name = "released_by", nullable = false)
    private UUID releasedBy;

    /**
     * True if admin used force release (overrode validation warnings/errors).
     */
    @Column(name = "force_released", nullable = false)
    @Builder.Default
    private Boolean forceReleased = false;
}
