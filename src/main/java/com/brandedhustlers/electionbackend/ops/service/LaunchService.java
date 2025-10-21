package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.domain.LaunchElection;
import com.brandedhustlers.electionbackend.ops.domain.LaunchElectionId;
import com.brandedhustlers.electionbackend.ops.dto.LaunchDto;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import com.brandedhustlers.electionbackend.ops.exception.DisallowedAction;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.exception.OrgMismatch;
import com.brandedhustlers.electionbackend.ops.mappers.LaunchMapper;
import com.brandedhustlers.electionbackend.ops.repository.LaunchElectionRepository;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;


/**
 * LaunchServiceImpl: Implements guarded lifecycle for Launch aggregates and the Launch↔Election linkage.
 * Justification: centralizes invariants (status transitions, org scoping) so controllers remain thin and
 * other modules do not duplicate rules. All writes are transactional to ensure atomic state updates and
 * audit emissions (hook points left as TODOs) happen consistently.
 */
@Service
public class LaunchService {
    private final LaunchRepository launchRepository;
    private final LaunchElectionRepository links;
    private final LaunchMapper launchMapper;


    public LaunchService(LaunchRepository launchRepository,
                         LaunchElectionRepository links,
                         LaunchMapper launchMapper) {
        this.launchRepository = launchRepository;
        this.links = links;
        this.launchMapper = launchMapper;
    }


    @Transactional
    public LaunchDto create(UUID orgId, String name, String description, Instant startAt, Instant endAt) {
        // Guard: basic validation belongs here (service boundary is where business rules live)

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("OPS-4000 name is required");
        }
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new IllegalStateException("OPS-4001 invalid time window");
        }

        Launch newLaunch = Launch.builder().id(UUID.randomUUID()).orgId(orgId).name(name).description(description).status(LaunchStatus.PLANNED).startAt(startAt).endAt(endAt).createdAt(Instant.now()).build();

        // TODO: audit.emit("ops.launch.created", saved)

        return launchMapper.entityToDto(launchRepository.save(newLaunch));
    }


    public LaunchDto get(UUID orgId, UUID launchId) {
        Optional<Launch> launch = launchRepository.findById(launchId).filter(l -> l.getOrgId().equals(orgId));

        if (launch.isEmpty()) {
            throw new LaunchNotfound("Launch with ID: " + launchId + " for org with ID: " + orgId + " not found.");
        }

        Launch validLaunch = launch.get();

        return launchMapper.entityToDto(validLaunch);
    }


    public Page<LaunchDto> list(UUID orgId, LaunchStatus status, String q, Pageable pageable) {

        List<Launch> base = (status == null) ? launchRepository.findAll() : launchRepository.findByOrgIdAndStatus(orgId, status);

        List<LaunchDto> launchDtos = base.stream()
                .filter(launch -> launch.getOrgId().equals(orgId))
                .filter(launch -> q == null || q.isBlank() || (launch.getName() + " " + Objects.toString(launch.getDescription(), ""))
                        .toLowerCase()
                        .contains(q.toLowerCase()))
                .map(launchMapper::entityToDto)
                .toList();

        return new PageImpl<>(launchDtos, pageable, launchDtos.size());
    }


    public void attachElections(UUID orgId, UUID launchId, List<UUID> electionIds) {
        Launch l = launchRepository.findById(launchId).orElseThrow(() -> new NoSuchElementException("Launch not found"));

        if (!l.getOrgId().equals(orgId)) {
            throw new SecurityException("OPS-4031 org mismatch");
        }

        if (l.getStatus() == LaunchStatus.COMPLETED) {
            throw new IllegalStateException("OPS-4001 cannot attach on COMPLETED");
        }

        for (UUID eid : electionIds) {
            LaunchElectionId id = new LaunchElectionId(launchId, eid);
            links.findById(id.getLaunchId()).ifPresentOrElse(le -> { /* already attached → idempotent no-op */ }, () -> links.save(LaunchElection.builder().id(id).build()));

            // TODO: audit.emit("ops.launch.election_attached", ...)
        }
    }


    public void detachElections(UUID orgId, UUID launchId, List<UUID> electionIds) {
        Launch l = launchRepository.findById(launchId).orElseThrow(() -> new LaunchNotfound("Launch not found"));
        if (!l.getOrgId().equals(orgId)) {
            throw new OrgMismatch("OPS-4031 org mismatch");
        }

        if (l.getStatus() == LaunchStatus.COMPLETED) {
            throw new DisallowedAction("Cannot detach on COMPLETED");
        }

        for (UUID eid : electionIds) {
            LaunchElectionId id = new LaunchElectionId(launchId, eid);

            links.findById(id.getLaunchId()).ifPresent(links::delete);
            // TODO: audit.emit("ops.launch.election_detached", ...)
        }
    }


    public LaunchDto transitionStatus(UUID orgId, UUID launchId, LaunchStatus status) {
        Launch l = launchRepository.findById(launchId).orElseThrow(() -> new NoSuchElementException("OPS-4041 launch not found"));
        if (!l.getOrgId().equals(orgId)) {
            throw new SecurityException("OPS-4031 org mismatch");
        }

        if (l.getStatus() == LaunchStatus.PLANNED && status == LaunchStatus.ACTIVE) {
            l.setStatus(LaunchStatus.ACTIVE);
            if (l.getStartAt() == null) {
                l.setStartAt(Instant.now());
            }

        } else if (l.getStatus() == LaunchStatus.ACTIVE && status == LaunchStatus.COMPLETED) {
            l.setStatus(LaunchStatus.COMPLETED);
            if (l.getEndAt() == null) {
                l.setEndAt(Instant.now());
            }

        } else {
            throw new IllegalStateException("OPS-4001 invalid transition from " + l.getStatus() + " to " + status);
        }

        // TODO: audit.emit("ops.launch.status_changed", {from,to})

        return launchMapper.entityToDto(launchRepository.save(l));
    }
}