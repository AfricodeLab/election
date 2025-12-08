package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Stores the blockchain anchor for election config at go-live.
 *
 * WHY: When voting starts, we snapshot the config (candidates, times, rules)
 * and anchor the hash to the blockchain. This proves "these were the rules
 * when voting began."
 *
 * At release, we compare current config hash to this anchored hash.
 * Mismatch = something changed during voting = flag for review.
 */
@Entity
@Table(name = "election_config_anchor", schema = "voting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionConfigAnchor {

    @Id
    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    /**
     * Full config JSON stored locally (not on chain).
     * Contains: electionId, startTime, endTime, candidates[], etc.
     */
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    /**
     * SHA-256 hash of config_json (this is what goes on chain).
     */
    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    /**
     * Blockchain transaction ID from the anchor operation.
     * Used to prove this config was anchored at a specific time.
     */
    @Column(name = "chain_tx_id")
    private String chainTxId;

    @Column(name = "anchored_at", nullable = false)
    private Timestamp anchoredAt;
}
