package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ILaunchService {
    Launch create(UUID orgId, String name, String description, Instant startAt, Instant endAt);

    Optional<Launch> get(UUID orgId, UUID launchId);

    List<Launch> list(UUID orgId, LaunchStatus status, String q, int page, int size);

    void attachElections(UUID orgId, UUID launchId, List<UUID> electionIds);

    void detachElections(UUID orgId, UUID launchId, List<UUID> electionIds);

    Launch transitionStatus(UUID orgId, UUID launchId, LaunchStatus status);
}
