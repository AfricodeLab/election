package com.brandedhustlers.electionbackend.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LaunchElectionId implements Serializable {
    @Column(name = "launch_id")
    private UUID launchId;


    @Column(name = "election_id")
    private UUID electionId;
}