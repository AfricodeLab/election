package com.brandedhustlers.electionbackend.results.domain;

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
public class TallyId implements Serializable {

    @Column(name = "election_id")
    private UUID electionId;

    @Column(name = "candidate_id")
    private UUID candidateId;
}