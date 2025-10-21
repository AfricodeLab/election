package com.brandedhustlers.electionbackend.ops.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "launch_election", schema = "ops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LaunchElection {
    @EmbeddedId
    private LaunchElectionId id;
}