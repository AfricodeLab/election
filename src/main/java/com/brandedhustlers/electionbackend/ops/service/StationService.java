package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.domain.Station;
import com.brandedhustlers.electionbackend.ops.dto.StationDto;
import com.brandedhustlers.electionbackend.ops.dto.UpsertStation;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.exception.OrgMismatch;
import com.brandedhustlers.electionbackend.ops.mappers.StationMapper;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import com.brandedhustlers.electionbackend.ops.repository.StationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * StationService: CRUD for Station with org/launch guards.
 * Justification: isolates station metadata operations; keeps device flows separate for clarity/testing.
 */
@Service
public class StationService {
    private final StationRepository stationRepository;
    private final LaunchRepository launchRepository;
    private final StationMapper stationMapper;

    public StationService(StationRepository stationRepository,
                          LaunchRepository launchRepository,
                          StationMapper stationMapper) {
        this.stationRepository = stationRepository;
        this.launchRepository = launchRepository;
        this.stationMapper = stationMapper;
    }

    @Transactional
    public StationDto createStation(UUID orgId, UpsertStation createStation) {
        Launch launch = launchRepository.findById(createStation.launchId())
                .orElseThrow(() -> new LaunchNotfound("Launch not found"));

        if (!launch.getOrgId().equals(orgId)) {
            throw new OrgMismatch("Org mismatch");
        }
        if (createStation.launchName() == null || createStation.launchName().isBlank()) {
            throw new IllegalArgumentException("Station name is required");
        }
        Station station = Station.builder()
                .orgId(orgId)
                .launchId(createStation.launchId())
                .name(createStation.launchName())
                .location(createStation.location())
                .capacity(createStation.capacity())
                .createdAt(Instant.now())
                .build();

        // TODO: audit.emit("ops.station.created", saved)
        return stationMapper
                .entityToDto(stationRepository.save(station));
    }

    public Optional<Station> getStation(UUID orgId, UUID stationId) {
        return stationRepository.findById(stationId)
                .filter(s -> s.getOrgId().equals(orgId));
    }

    public Page<StationDto> listStationByLaunch(UUID orgId, UUID launchId, Pageable pageable) {
        List<StationDto> stations = stationRepository
                .findByOrgIdAndLaunchId(orgId, launchId, pageable)
                .stream()
                .map(stationMapper::entityToDto)
                .toList();
        return new PageImpl<>(stations, pageable, stations.size());

    }

    @Transactional
    public StationDto updateStation(UUID orgId, UUID stationId, UpsertStation upsertStation) {
        Station s = stationRepository.findById(stationId)
                .orElseThrow(() -> new NoSuchElementException("OPS-4042 station not found"));

        if (!s.getOrgId().equals(orgId)) {
            throw new SecurityException("OPS-4031 org mismatch");
        }

        if (upsertStation.launchName() != null && !upsertStation.launchName().isBlank()) {
            s.setName(upsertStation.launchName());
        }

        if (upsertStation.location() != null) {
            s.setLocation(upsertStation.location());
        }
        if (upsertStation.capacity() != null) {
            s.setCapacity(upsertStation.capacity());
        }

        // TODO: audit.emit("ops.station.updated", {...diffs})

        return stationMapper
                .entityToDto(stationRepository.save(s));
    }
}