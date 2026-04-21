package com.brandedhustlers.electionbackend.voter.domain;

import com.brandedhustlers.electionbackend.election.domain.Election;
import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(
        name = "election_voter",
        schema = "voter"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionVoter {

    @EmbeddedId
    private ElectionVoterId id;

    @MapsId("voterId")
    @JoinColumn(name = "voter_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Voter voter;

    @MapsId("electionId")
    @JoinColumn(name = "election_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Election election;

    private UUID stationId;

    /** Timestamp at which this voter's identity was verified. */
    private Timestamp verifiedAt;

    private Timestamp votedAt;

    private boolean hasVoted;

    // ── Verification state ────────────────────────────────────────────────────

    /**
     * True once identity has been confirmed — either via OTP (remote) or by an
     * official (in-person). The vote-cast endpoint enforces this before accepting
     * a ballot.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    /**
     * The user ID of the admin/official who verified this voter in person.
     * Null for self-service OTP verification.
     */
    @Column(name = "verified_by")
    private UUID verifiedBy;

    /**
     * How the voter was verified: {@code REMOTE_OTP} or {@code IN_PERSON}.
     * Null if not yet verified.
     */
    @Column(name = "verification_mode", length = 20)
    private String verificationMode;
}
