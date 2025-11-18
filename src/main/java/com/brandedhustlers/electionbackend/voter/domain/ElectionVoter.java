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

    private Timestamp verifiedAt;

    private boolean hasVoted;
}
