package com.brandedhustlers.electionbackend.voter.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(
        name = "eligible_voter_election",
        schema = "voter",
        uniqueConstraints = @UniqueConstraint(columnNames = {"voter_id", "election_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibleVoterElection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    // Nullable: set when the election is part of a Launch; null for standalone elections
    // brought live individually via POST /election/live/{id}.
    @Column(name = "launch_id", nullable = true)
    private UUID launchId;

    @Column(name = "computed_at", nullable = false)
    private Timestamp computedAt;
}
