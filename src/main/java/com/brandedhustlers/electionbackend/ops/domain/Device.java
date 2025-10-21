package com.brandedhustlers.electionbackend.ops.domain;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device", schema = "ops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {
    @Id @Column(name = "device_id")
    private UUID id;


    @Column(name = "org_id", nullable = false)
    private UUID orgId;


    @Column(name = "station_id", nullable = false)
    private UUID stationId;


    @Column(nullable = false)
    private String label; // human-friendly identifier within org


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status;


    @Column(name = "last_seen")
    private Instant lastSeen;


    @Column(name = "last_latency_ms")
    private Long lastLatencyMs;


    @Column(name = "app_version")
    private String appVersion;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;

}