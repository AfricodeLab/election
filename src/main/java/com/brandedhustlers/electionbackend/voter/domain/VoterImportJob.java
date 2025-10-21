package com.brandedhustlers.electionbackend.voter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "voter_import_job", schema = "voter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoterImportJob {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String fileRef;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int processed;

    @Column(nullable = false)
    private int failed;

    @Column(nullable = false)
    private String status; // PENDING, RUNNING, DONE, FAILED

    private Timestamp startedAt;

    private Timestamp finishedAt;
}


