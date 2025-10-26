package com.brandedhustlers.electionbackend.ops.domain;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "launch", schema = "ops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Launch {
    @Id @Column(name = "launch_id")
    private UUID id;


    @Column(name = "org_id", nullable = false)
    private UUID orgId;


    @Column(nullable = false)
    private String name;


    @Column
    private String description;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LaunchStatus status;


    @Column(name = "start_at")
    private Instant startAt; // optional operational window


    @Column(name = "end_at")
    private Instant endAt;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany
    private List<Election> election;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;
}
