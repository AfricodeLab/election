package com.brandedhustlers.electionbackend.results.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.sql.Timestamp;

@Entity
@Table(name = "tally", schema = "results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tally {
    @EmbeddedId
    private TallyId id;

    @Column(nullable = false)
    private long count;
    
    @Column(nullable = false)
    private Timestamp updatedAt;
}
