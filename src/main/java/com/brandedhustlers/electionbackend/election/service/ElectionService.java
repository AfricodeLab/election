package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.domain.*;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.election.mapper.CandidateMapper;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionPresetRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionTypeRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voting.service.ConfigAnchorService;
import com.brandedhustlers.electionbackend.voting.service.ConfigSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElectionService implements IElectionService {
    private final ElectionRepository electionRepository;
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;
    private final ElectionMapper electionMapper;
    private final CloudinaryService cloudinaryService;
    private final CandidateRepository candidateRepository;
    private final AuditEmitter auditEmitter;
    private final CandidateMapper candidateMapper;
    private final GetUser getUser;
    private final ConfigSnapshotService configSnapshotService;
    private final ConfigAnchorService configAnchorService;
    private final HttpServletRequest httpServletRequest;
    private final VoterRepository voterRepository;
    private final ElectionTypeRepository electionTypeRepository;
    private final ElectionPresetRepository electionPresetRepository;

    public ElectionService(ElectionRepository electionRepository,
            JWTService jwtService,
            AppUserRepository appUserRepository,
            ElectionMapper electionMapper,
            CloudinaryService cloudinaryService,
            CandidateRepository candidateRepository,
            AuditEmitter auditEmitter,
            CandidateMapper candidateMapper,
            GetUser getUser,
            ConfigSnapshotService configSnapshotService,
            ConfigAnchorService configAnchorService,
            HttpServletRequest httpServletRequest,
            VoterRepository voterRepository,
            ElectionTypeRepository electionTypeRepository,
            ElectionPresetRepository electionPresetRepository) {
        this.electionRepository = electionRepository;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.electionMapper = electionMapper;
        this.cloudinaryService = cloudinaryService;
        this.candidateRepository = candidateRepository;
        this.auditEmitter = auditEmitter;
        this.candidateMapper = candidateMapper;
        this.getUser = getUser;
        this.configSnapshotService = configSnapshotService;
        this.configAnchorService = configAnchorService;
        this.httpServletRequest = httpServletRequest;
        this.voterRepository = voterRepository;
        this.electionTypeRepository = electionTypeRepository;
        this.electionPresetRepository = electionPresetRepository;
    }

    @Override
    @Transactional
    public ElectionDTO createElection(CreateElectionRequest request) {
        AppUser creator = getUser.getCurrentAuthenticatedUser();

        if (!(authorizeUser(creator, UserRole.ADMIN) || authorizeUser(creator, UserRole.SUPER_ADMIN))) {
            throw new AccessDeniedException("You do not have authority to create an election");
        }

        if (creator.getAppUserOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create an election");
        }

        Organization electionOrg = creator.getAppUserOrganization();

        // Resolve election type
        if (request.typeId() == null) {
            throw new InvalidUpdate("An election type must be specified (typeId)");
        }
        UUID electionTypeId = UUID.fromString(request.typeId());

        log.debug("Election Type ID: {}", electionTypeId);

        ElectionTypeEntity electionType = electionTypeRepository.findByElectionTypeId(electionTypeId)
                .orElseThrow(() -> new InvalidUpdate("Election type not found: " + request.typeId()));

        log.debug("election entity: {}", electionType);

        // Resolve preset defaults (if presetId was supplied)
        ElectionPreset resolvedPreset = null;
        EligibilityPolicy effectivePolicy = request.electionEligibilityPolicy();
        Long effectiveVoterCount = request.expectedVoterCount();
        boolean effectiveShouldEnd = request.shouldEndAutomatically();

        if (request.presetId() != null) {
            resolvedPreset = electionPresetRepository.findByPresetIdAndDeleteYn(
                    request.presetId(), DeleteYn.N)
                    .orElseThrow(() -> new InvalidUpdate("Preset not found: " + request.presetId()));

            // Apply preset defaults only if the caller did not override them
            if (effectivePolicy == null) {
                effectivePolicy = resolvedPreset.getDefaultEligibilityPolicy();
            }
            if (effectiveVoterCount == null) {
                effectiveVoterCount = resolvedPreset.getDefaultExpectedVoterCount();
            }
        }

        Election newElection = Election.builder()
                .electionTitle(request.electionTitle())
                .electionDescription(request.electionDescription())
                .electionType(electionType)
                .presetUsed(resolvedPreset)
                .electionStatus(ElectionStatus.DRAFT)
                .electionStartTime(request.startTime())
                .electionEndTime(request.endtime())
                .expectedVoterCount(effectiveVoterCount)
                .isPubliclyVisible(request.isPubliclyVisible())
                .shouldEndAutomatically(effectiveShouldEnd)
                .eligibilityPolicy(effectivePolicy)
                .organization(electionOrg)
                .createdBy(creator)
                .deleteYn(DeleteYn.N)
                .build();

        if (!request.startTime().toLocalDateTime().isBefore(request.endtime().toLocalDateTime())) {
            throw new InvalidUpdate("Start time must be before end time");
        }

        if (electionRepository.existsByElectionTitleAndOrganization(
                request.electionTitle(), creator.getAppUserOrganization())) {
            throw new InvalidUpdate("An election with this title already exists in your organization");
        }
        Election election = electionRepository.save(newElection);

        List<Candidate> electionCandidates = new ArrayList<>();
        if (!request.candidates().isEmpty()) {
            for (CandidateDTO dto : request.candidates()) {
                Candidate candidate = candidateMapper.dtoToEntity(dto);
                candidate.setElection(election);
                candidate.setCreatedBy(creator);
                electionCandidates.add(candidate);
            }
        }
        newElection.setCandidates(electionCandidates);

        UUID actorId = creator.getAppUserId();
        String actorName = creator.getFirstName() + " " + creator.getLastName();
        String actorRole = creator.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "election.new_election_created",
                null,
                creator.getAppUserOrganization().getOrganizationId(),
                "New Election created",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Create Election",
                election.getElectionId(),
                election.getElectionTitle(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1);

        return electionMapper.entityToDto(
                election);
    }

    @Override
    public ElectionDTO updateElection(UUID electionID, UpdateElection request) {
        AppUser updater = getUser.getCurrentAuthenticatedUser();

        Election electionToUpdate = electionRepository.findById(electionID)
                .orElseThrow(() -> new ElectionNotFound("An election with this ID: " + electionID + " does not exist"));

        if (!electionToUpdate.getOrganization().getOrganizationId()
                .equals(updater.getAppUserOrganization().getOrganizationId())) {
            throw new AccessDeniedException("You cannot manage elections outside your organization");
        }

        requireAdminOrPermission(updater, PermissionType.EDIT_ELECTIONS);

        if (!electionToUpdate.getElectionStatus().equals(ElectionStatus.DRAFT)) {
            throw new InvalidUpdate("The selected election is not in draft and can no longer be updated");
        }
        // updateStation can happen now all checks have been made
        electionToUpdate.setElectionTitle(request.newElectionName());
        electionToUpdate.setElectionStatus(request.newElectionStatus());
        electionToUpdate.setElectionStartTime(request.newStartTime());
        electionToUpdate.setElectionEndTime(request.newEndtime());
        electionToUpdate.setUpdatedBy(updater);

        Election updated = electionRepository.save(electionToUpdate);

        UUID actorId = updater.getAppUserId();
        String actorName = updater.getFirstName() + " " + updater.getLastName();
        String actorRole = updater.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "election.election_updated",
                null,
                updater.getAppUserOrganization().getOrganizationId(),
                "Election details updated",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                updated.getElectionId(),
                updated.getElectionTitle(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1);

        return electionMapper.entityToDto(
                updated);
    }

    @Override
    public ElectionDTO changeElectionStatus(UUID electionId, ElectionStatus newStatus) {
        AppUser updater = getUser.getCurrentAuthenticatedUser();

        Election statusChange = electionRepository.findById(electionId)
                .orElseThrow(
                        () -> new ElectionNotFound("An election with this ID: " + electionId + " does not exist."));

        ElectionStatus currStatus = statusChange.getElectionStatus();

        if (!statusChange.getOrganization().getOrganizationId()
                .equals(updater.getAppUserOrganization().getOrganizationId())) {
            throw new AccessDeniedException("You cannot manage elections outside your organization");
        }

        requireAdminOrPermission(updater, PermissionType.EDIT_ELECTIONS);

        if (statusChange.getElectionStatus() == ElectionStatus.ENDED) {
            throw new InvalidUpdate("This election has already ended and cannot be modified.");
        }

        if (!statusChange.getElectionStatus().equals(ElectionStatus.DRAFT)) {

        }
        statusChange.setElectionStatus(newStatus);

        Election statChange = electionRepository.save(statusChange);

        UUID actorId = updater.getAppUserId();
        String actorName = updater.getFirstName() + " " + updater.getLastName();
        String actorRole = updater.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "election.status_change",
                null,
                updater.getAppUserOrganization().getOrganizationId(),
                "Election status updated from " + currStatus + " to: " + statChange.getElectionStatus(),
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                statChange.getElectionId(),
                statChange.getElectionTitle(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1);

        return electionMapper.entityToDto(statChange);
    }

    @Override
    @Transactional
    public ElectionDTO addCandidate(UUID electionId, CandidateDTO newCandidate, MultipartFile candidateImage)
            throws IOException {
        AppUser creator = getUser.getCurrentAuthenticatedUser();

        Election election = electionRepository.findById(electionId)
                .orElseThrow(
                        () -> new ElectionNotFound("An election with this ID: " + electionId + " does not exist."));

        requireAdminOrPermission(creator, PermissionType.EDIT_ELECTIONS);

        if (!election.getElectionStatus().equals(ElectionStatus.DRAFT)) {
            throw new InvalidUpdate("Cannot add candidates to an election that is not in the drafting stage.");
        }

        Candidate candidate = Candidate.builder()
                .affiliation(newCandidate.affiliation() == null ? "Independent Candidate" : newCandidate.affiliation())
                .manifesto(newCandidate.manifesto())
                .candidatePhotoURL(candidateImage != null ? cloudinaryService.uploadFile(candidateImage) : null)
                .dateOfBirth(newCandidate.dateOfBirth())
                .isIncumbent(newCandidate.isIncumbent())
                .createdBy(creator)
                .deleteYn(DeleteYn.N)
                .build();
        // add to election
        List<Candidate> electionCandidates = election.getCandidates();
        candidate.setElection(election);

        electionCandidates.add(candidate);
        election.setCandidates(electionCandidates);

        candidateRepository.save(candidate);
        Election addCandidate = electionRepository.save(election);

        UUID actorId = creator.getAppUserId();
        String actorName = creator.getFirstName() + " " + creator.getLastName();
        String actorRole = creator.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "election.election_updated",
                null,
                creator.getAppUserOrganization().getOrganizationId(),
                "Election details updated",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Launch",
                addCandidate.getElectionId(),
                addCandidate.getElectionTitle(),
                source,
                null,
                ip,
                ua,
                "INFO",
                1);

        return electionMapper.entityToDto(
                addCandidate);
    }

    public Page<ElectionDTO> getElectionsByOrg(UUID organizationId, Pageable pageable) {
        List<ElectionDTO> elections = electionRepository.findByOrganizationId(organizationId)
                .stream()
                .map(electionMapper::entityToDto)
                .toList();
        return new PageImpl<>(elections, pageable, elections.size());
    }

    public Page<ElectionDTO> getLiveElectionForVoterOrg(Pageable pageable) {

        Voter currentVoter = getUser.getCurrentAuthenticatedVoter();
        UUID voterOrgId = currentVoter.getOrganization().getOrganizationId();
        UUID voterId = currentVoter.getVoterId();

        List<ElectionDTO> unvotedElections = electionRepository.findLiveByOrgAndNotVoted(
                voterOrgId,
                ElectionStatus.LIVE,
                voterId)
                .stream()
                .map(electionMapper::entityToDto)
                .toList();

        return new PageImpl<>(unvotedElections, pageable, unvotedElections.size());
    }

    /**
     * Transition election to LIVE status.
     * This is when voting begins.
     */
    @Transactional
    public void transitionToLive(UUID electionId, UUID adminId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        if (election.getElectionStatus() == ElectionStatus.LIVE) {
            throw new IllegalStateException("Election already live");
        }

        /**
         * Step 1: Capture GO_LIVE config snapshot.
         * This is our baseline for detecting changes later.
         */
        configSnapshotService.captureSnapshot(
                electionId,
                "GO_LIVE",
                adminId,
                "Initial config snapshot at go-live");

        /**
         * Step 2: Anchor config to blockchain.
         * This creates cryptographic proof of what the config was.
         * (Implementation in next section)
         */
        configAnchorService.anchorConfig(electionId);

        /**
         * Step 3: Update election status.
         */
        election.setElectionStatus(ElectionStatus.LIVE);
        electionRepository.save(election);

        // todo emit election live even so participants reverive email
        log.info("Election {} transitioned to LIVE, config anchored", electionId);

        // return electionMapper.entityToDto(election);
    }

    private boolean authorityCheck(UUID appUserId, PermissionType allowedPermission) {
        AppUser updatingUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        Set<PermissionType> userPermissions = updatingUser.getRoles()
                .stream()
                .flatMap(role -> role.getRolePermissions().stream())
                .map(Permission::getPermissionType)
                .collect(Collectors.toSet());

        return userPermissions.contains(allowedPermission);
    }

    private boolean authorizeUser(AppUser user, UserRole userType) {
        return user.getUserRole().equals(userType);
    }

    private void requireAdminOrPermission(AppUser user, PermissionType permission) {
        boolean hasRole = authorizeUser(user, UserRole.ADMIN) || authorizeUser(user, UserRole.SUPER_ADMIN);
        boolean hasPermission = authorityCheck(user.getAppUserId(), permission);

        if (!hasRole && !hasPermission)
            throw new AccessDeniedException("You lack sufficient privileges for this action");
    }

}
