package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
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
import java.util.stream.Collectors;


/**
 * LaunchServiceImpl: Implements guarded lifecycle for Launch aggregates and the Launch↔Election linkage.
 * Justification: centralizes invariants (status transitions, org scoping) so controllers remain thin and
 * other modules do not duplicate rules. All writes are transactional to ensure atomic state updates and
 * audit emissions happen consistently.
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
    private final GetUser getUser;
    private final ElectionService electionService;


    public LaunchService(LaunchRepository launchRepository,
                         LaunchElectionRepository links,
                         LaunchMapper launchMapper,
                         RabbitTemplate rabbitTemplate,
                         AuditEmitter auditEmitter,
                         ElectionRepository electionRepository,
                         ElectionMapper electionMapper, GetUser getUser, ElectionService electionService) {
        this.launchRepository = launchRepository;
        this.links = links;
        this.launchMapper = launchMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.auditEmitter = auditEmitter;
        this.electionRepository = electionRepository;
        this.electionMapper = electionMapper;
        this.getUser = getUser;
        this.electionService = electionService;
    }


    @Transactional
    public LaunchDto create(UUID orgId, String name, String description, Instant startAt, Instant endAt) {
        // Guard: basic validation belongs here (service boundary is where business rules live)
        AppUser actor = getUser.getCurrentAuthenticatedUser();

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
        Optional<Launch> launch = launchRepository.findByLaunchId(launchId)
                .filter(l -> l.getOrgId().equals(orgId));
        if (launch.isEmpty()) {
            throw new LaunchNotfound("Launch with ID: " + launchId + " for org with ID: " + orgId + " not found.");
        }

        Launch validLaunch = launch.get();
        List<ElectionDTO> electionsAssociatedWithLaunch = links.findByIdLaunchId(launchId)
                .stream()
                .map(LaunchElection::getId)
                .map(LaunchElectionId ::getElectionId)
                .map(electionRepository::findById)
                .flatMap(Optional::stream)
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


    public Page<LaunchDto> list(UUID orgId, LaunchStatus status, Pageable pageable) {

        List<Launch> base = (status == null) ? launchRepository.findAll() : launchRepository.findByOrgIdAndStatus(orgId, status);

        List<LaunchDto> launchDtos = base.stream()
                .filter(launch -> launch.getOrgId().equals(orgId))
                .filter(launch -> launch.getDeleteYn().equals(DeleteYn.N))
                .map(launchMapper::entityToDto)
                .toList();

        return new PageImpl<>(launchDtos, pageable, launchDtos.size());
    }


    public void attachElections(UUID orgId, UUID launchId, List<UUID> electionIds) {

        AppUser actor = getUser.getCurrentAuthenticatedUser();

        Launch l = launchRepository.findByLaunchId(launchId)
                .orElseThrow(() -> new NoSuchElementException("Launch not found"));

        if (!l.getOrgId().equals(orgId)) {
            throw new SecurityException("Org mismatch");
        }

        if (l.getStatus() == LaunchStatus.COMPLETED) {
            throw new IllegalStateException("Cannot attach on COMPLETED");
        }

        //pre-build composite IDs
        List<LaunchElectionId> linkIds = electionIds.stream()
                .map(eid -> new LaunchElectionId(launchId, eid))
                .toList();

        //fetch all existing links in a single query
        Set<LaunchElectionId> existingLinks = links.findAllById(linkIds).stream()
                .map(LaunchElection::getId)
                .collect(Collectors.toSet());

        //save missing links in batch
        List<LaunchElection> toSave = linkIds.stream()
                .filter(id -> !existingLinks.contains(id))
                .map(id -> LaunchElection.builder().id(id).build())
                .toList();
        links.saveAll(toSave); // batch insert

        //fetch all elections in a single query
        List<Election> elections = electionRepository.findAllById(electionIds);

        //set the launch on all elections
        elections.forEach(e -> e.setLaunch(l));

        //save all elections in batch
        electionRepository.saveAll(elections);


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


    public void detachElections(UUID orgId, UUID launchId, List<UUID> electionIds) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        Launch l = launchRepository.findByLaunchId(launchId)
                .orElseThrow(() -> new LaunchNotfound("Launch not found"));
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
    public GetLaunch transitionStatus(UUID orgId, UUID launchId, LaunchStatus status) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        Launch l = launchRepository.findByLaunchId(launchId)
                .orElseThrow(() -> new NoSuchElementException("OPS-4041 launch not found"));
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
                electionService.transitionToLive(election.getElectionId(), actor.getAppUserId());
            }

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

//todo end election functionality