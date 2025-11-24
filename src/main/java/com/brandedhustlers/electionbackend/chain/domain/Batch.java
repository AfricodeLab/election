package com.brandedhustlers.electionbackend.chain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "anchor_batch", schema = "chain"
)
public class Batch {
    @Id
    private UUID batchId;

    private UUID electionId;

    private UUID orgId;

    private String merkleRoot;

    private Timestamp fromTs;

    private Timestamp toTs;

    private String status;

    private String txId;

    private Timestamp anchoredAt;

    private Timestamp createdAt;

    /**
     * Number of votes included in this batch's Merkle tree.
     * Helps with validation (should match outbox count for this batch).
     */
    @Column(name = "vote_count")
    private Integer voteCount;

    private String chainTxId;
}
