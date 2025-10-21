package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.ops.domain.Station;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IStationService {
    Station create(UUID orgId, UUID launchId, String name, String location, Integer capacity);

    Optional<Station> get(UUID orgId, UUID stationId);

    List<Station> listByLaunch(UUID orgId, UUID launchId);

    Station update(UUID orgId, UUID stationId, String name, String location, Integer capacity);
}
