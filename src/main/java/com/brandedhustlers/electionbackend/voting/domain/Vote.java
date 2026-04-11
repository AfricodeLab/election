package com.brandedhustlers.electionbackend.voting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
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
    private Instant castAt;

/*    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "election_id", referencedColumnName = "election_id", insertable = false, updatable = false),
            @JoinColumn(name = "voter_id", referencedColumnName = "voter_id", insertable = false, updatable = false)
    })
    private ElectionVoter electionVoter;*/
}
//todo validate that every vote is from a valid voter
//todo validate that every vote is a valid voter; tag the api or the outbox