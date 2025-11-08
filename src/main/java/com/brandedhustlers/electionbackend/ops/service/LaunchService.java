package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.domain.LaunchElection;
import com.brandedhustlers.electionbackend.ops.domain.LaunchElectionId;
import com.brandedhustlers.electionbackend.ops.dto.GetLaunch;
import com.brandedhustlers.electionbackend.ops.dto.LaunchDto;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import com.brandedhustlers.electionbackend.ops.exception.DisallowedAction;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.exception.OrgMismatch;
import com.brandedhustlers.electionbackend.ops.mappers.LaunchMapper;
import com.brandedhustlers.electionbackend.ops.repository.LaunchElectionRepository;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;
    private final AuditEmitter auditEmitter;
    private final ElectionRepository electionRepository;
    private final ElectionMapper electionMapper;


    public LaunchService(LaunchRepository launchRepository,
                         LaunchElectionRepository links,
                         LaunchMapper launchMapper,
                         RabbitTemplate rabbitTemplate,
                         AuditEmitter auditEmitter,
                         ElectionRepository electionRepository,
                         ElectionMapper electionMapper) {
        this.launchRepository = launchRepository;
        this.links = links;
        this.launchMapper = launchMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.auditEmitter = auditEmitter;
        this.electionRepository = electionRepository;
        this.electionMapper = electionMapper;
    }


    @Transactional
    public LaunchDto create(UUID orgId, String name, String description, Instant startAt, Instant endAt, AppUser actor) {
        // Guard: basic validation belongs here (service boundary is where business rules live)

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("OPS-4000 name is required");
        }
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new IllegalStateException("OPS-4001 invalid time window");
        }

        Launch newLaunch = Launch.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .name(name)
                .description(description)
                .status(LaunchStatus.PLANNED)
                .startAt(startAt)
                .endAt(endAt)
                .createdAt(Instant.now())
                .deleteYn(DeleteYn.N)
                .build();

        Launch saved = launchRepository.save(newLaunch);

        UUID actorId = actor.getAppUserId();
        String actorName = actor.getFirstName();
        String actorRole = actor.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "ops.launch.created",
                null,
                orgId,
                "Launch created",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                saved.getId(),
                saved.getName(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1
        );

        return launchMapper.entityToDto(saved);
    }


    public GetLaunch get(UUID orgId, UUID launchId) {
        Optional<Launch> launch = launchRepository.findById(launchId).filter(l -> l.getOrgId().equals(orgId));

        if (launch.isEmpty()) {
            throw new LaunchNotfound("Launch with ID: " + launchId + " for org with ID: " + orgId + " not found.");
        }

        //TODO: add pagination

        Launch validLaunch = launch.get();
        List<ElectionDTO> electionsAssociatedWithLaunch = links.findByIdLaunchId(launchId)
                .stream()
                .map(LaunchElection::getId)
                .map(LaunchElectionId ::getElectionId)
                .map(electionRepository::findById)
                .map(Optional::get)
                .map(electionMapper::entityToDto)
                .toList();



        return new GetLaunch(
                validLaunch.getId(),
                validLaunch.getOrgId(),
                validLaunch.getName(),
                validLaunch.getDescription(),
                validLaunch.getStatus(),
                validLaunch.getStartAt(),
                validLaunch.getEndAt(),
                validLaunch.getCreatedAt(),
                electionsAssociatedWithLaunch
        );
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


    public void attachElections(UUID orgId, UUID launchId, List<UUID> electionIds, AppUser actor) {
        Launch l = launchRepository.findById(launchId).orElseThrow(() -> new NoSuchElementException("Launch not found"));

        if (!l.getOrgId().equals(orgId)) {
            throw new SecurityException("Org mismatch");
        }

        if (l.getStatus() == LaunchStatus.COMPLETED) {
            throw new IllegalStateException("Cannot attach on COMPLETED");
        }

        for (UUID eid : electionIds) {
            LaunchElectionId id = new LaunchElectionId(launchId, eid);
            links.findById(id)
                    .ifPresentOrElse(le -> { /* already attached → idempotent no-op */ }, () -> links.save(LaunchElection.builder().id(id).build()));

        }
        // TODO: audit.emit("ops.launch.election_attached", ...)
        UUID actorId = actor.getAppUserId();
        String actorName = actor.getFirstName();
        String actorRole = actor.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "ops.launch.election_attached",
                null,
                orgId,
                "Election attached to launch.",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                l.getId(),
                l.getName(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1
        );
    }


    public void detachElections(UUID orgId, UUID launchId, List<UUID> electionIds, AppUser actor) {
        Launch l = launchRepository.findById(launchId).orElseThrow(() -> new LaunchNotfound("Launch not found"));
        if (!l.getOrgId().equals(orgId)) {
            throw new OrgMismatch("OPS-4031 org mismatch");
        }

        if (l.getStatus() == LaunchStatus.COMPLETED) {
            throw new DisallowedAction("Cannot detach on COMPLETED");
        }

        for (UUID eid : electionIds) {
            LaunchElectionId id = new LaunchElectionId(launchId, eid);
            links.findById(id).ifPresent(links::delete);
        }

        UUID actorId = actor.getAppUserId();
        String actorName = actor.getFirstName();
        String actorRole = actor.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "ops.launch.election_detached",
                null,
                orgId,
                "Election detached from launch.",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                l.getId(),
                l.getName(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1
        );
    }


    @Transactional
    public GetLaunch transitionStatus(UUID orgId, UUID launchId, LaunchStatus status, AppUser actor) {
        Launch l = launchRepository.findById(launchId).orElseThrow(() -> new NoSuchElementException("OPS-4041 launch not found")); // todo filter by delete yn
        LaunchStatus currStatus = l.getStatus();

        if (!l.getOrgId().equals(orgId)) {
            throw new SecurityException("OPS-4031 org mismatch");
        }

        if (l.getStatus() == LaunchStatus.PLANNED && status == LaunchStatus.ACTIVE) {
            l.setStatus(LaunchStatus.ACTIVE);

            Map<String, Object> launchLivePayload = Map.of(
                    "launchId", l.getId(),
                    "orgId", orgId
            );
            if (l.getStartAt() == null) {
                l.setStartAt(Instant.now());
            }

            List<Election> launchElections = l.getElection();
            for(Election election : launchElections){
                election.setElectionStatus(ElectionStatus.LIVE);
            }
            electionRepository.saveAll(launchElections);

            rabbitTemplate.convertAndSend("notif.email", launchLivePayload);

        } else if (l.getStatus() == LaunchStatus.ACTIVE && status == LaunchStatus.COMPLETED) {
            l.setStatus(LaunchStatus.COMPLETED);
            if (l.getEndAt() == null) {
                l.setEndAt(Instant.now());
            }

        } else {
            throw new IllegalStateException("Invalid transition from " + l.getStatus() + " to " + status);
        }

        Launch transition = launchRepository.save(l);

        // TODO: audit.emit("ops.launch.status_changed", {from,to})

        UUID actorId = actor.getAppUserId();
        String actorName = actor.getFirstName() + " " + actor.getLastName();
        String actorRole = actor.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "ops.launch.status_changed from " + currStatus.name() + " to " + l.getStatus(),
                null,
                orgId,
                "Launch status changed from " + currStatus.name() + " to " + l.getStatus() + ".",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                l.getId(),
                l.getName(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1
        );
        List<ElectionDTO> liveElections = l.getElection()
                .stream()
                .map(electionMapper::entityToDto)
                .toList();

        return new GetLaunch(
                transition.getId(),
                transition.getOrgId(),
                transition.getName(),
                transition.getDescription(),
                transition.getStatus(),
                transition.getStartAt(),
                transition.getEndAt(),
                transition.getCreatedAt(),
                liveElections
        );
    }
}