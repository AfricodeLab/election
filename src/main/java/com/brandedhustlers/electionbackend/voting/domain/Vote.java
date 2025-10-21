package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "vote", schema = "voting",
        indexes = {
                @Index(name = "idx_vote_election", columnList = "election_id"),
                @Index(name = "idx_vote_candidate", columnList = "candidate_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {
    @Id
    @Column(name = "vote_id")
    private UUID id;

    @Column(nullable = false)
    private UUID orgId;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "voter_id")
    private UUID voterId; // nullable for anon mode

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "dedupe_key", unique = true)
    private String dedupeKey;

    @Column(name = "proof_ref")
    private String proofRef;

    @Column(name = "tx_id")
    private String txId;

    @Column(name = "cast_at", nullable = false)
    private Timestamp castAt;
}
