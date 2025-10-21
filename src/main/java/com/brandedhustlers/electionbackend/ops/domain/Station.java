package com.brandedhustlers.electionbackend.ops.domain;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "station", schema = "ops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Station {
    @Id
    @Column(name = "station_id")
    private UUID id;


    @Column(name = "org_id", nullable = false)
    private UUID orgId;


    @Column(name = "launch_id", nullable = false)
    private UUID launchId;


    @Column(nullable = false)
    private String name;


    @Column
    private String location; // geo string or human-readable address


    @Column
    private Integer capacity; // optional, for ops planning


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;
}