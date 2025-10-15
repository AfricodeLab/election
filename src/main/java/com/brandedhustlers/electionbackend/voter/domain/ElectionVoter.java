package com.brandedhustlers.electionbackend.voter.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
public class ElectionVoter {
    @EmbeddedId
    private ElectionVoterId id;

    private UUID stationId;

    private Timestamp verifiedAt;

    private boolean hasVoted;
}
