package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ElectionService implements IElectionService {
    private final ElectionRepository electionRepository;
    private final ElectionMapper electionMapper;
    private final CloudinaryService cloudinaryService;
    private final CandidateRepository candidateRepository;
    private final AuditEmitter auditEmitter;
    private final CandidateMapper candidateMapper;
    private final GetUser getUser;
    private final ConfigSnapshotService configSnapshotService;
    private final ConfigAnchorService configAnchorService;
    private final Util util;
    private final ElectionTypeRepository electionTypeRepository;
    private final ElectionPresetRepository electionPresetRepository;
    private final EligibilityService eligibilityService;
    private final VoterRepository voterRepository;

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
                           VoterRepository voterRepository, Util util,
                           ElectionTypeRepository electionTypeRepository,
                           ElectionPresetRepository electionPresetRepository,
                           EligibilityService eligibilityService) {
        this.electionRepository = electionRepository;
        this.util = util;
        this.electionMapper = electionMapper;
        this.cloudinaryService = cloudinaryService;
        this.candidateRepository = candidateRepository;
        this.auditEmitter = auditEmitter;
        this.candidateMapper = candidateMapper;
        this.getUser = getUser;
        this.configSnapshotService = configSnapshotService;
        this.configAnchorService = configAnchorService;
        this.electionTypeRepository = electionTypeRepository;
        this.electionPresetRepository = electionPresetRepository;
        this.eligibilityService = eligibilityService;
        this.voterRepository = voterRepository;
    }

    @Override
    @Transactional
    public ElectionDTO createElection(CreateElectionRequest request) {
        AppUser creator = getUser.getCurrentAuthenticatedUser();

        if (!(util.authorizeUser(creator, UserRole.ADMIN)
                || util.authorizeUser(creator, UserRole.SUPER_ADMIN)
                || util.authorizeUser(creator, UserRole.ORG_ADMIN))) {
            throw new AccessDeniedException("You do not have authority to create an election");
        }

        if (creator.getActiveOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create an election");
        }

        Organization electionOrg = creator.getActiveOrganization();

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
                request.electionTitle(), creator.getActiveOrganization())) {
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
                creator.getActiveOrganization().getOrganizationId(),
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

        Organization updaterOrg = updater.getActiveOrganization();

        if (updaterOrg == null) {
            throw new AccessDeniedException("No active organization. Please join or switch to an organization first.");
        }

        if (!electionToUpdate.getOrganization().getOrganizationId()
                .equals(updaterOrg.getOrganizationId())) {
            throw new AccessDeniedException("You cannot manage elections outside your organization");
        }

        util.requireAdminOrPermission(updater, PermissionType.EDIT_ELECTIONS);

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
                updaterOrg.getOrganizationId(),
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

        Organization updaterOrg = updater.getActiveOrganization();
        if (updaterOrg == null) {
            throw new AccessDeniedException("No active organization. Please join or switch to an organization first.");
        }

        if (!statusChange.getOrganization().getOrganizationId()
                .equals(updaterOrg.getOrganizationId())) {
            throw new AccessDeniedException("You cannot manage elections outside your organization");
        }

        util.requireAdminOrPermission(updater, PermissionType.EDIT_ELECTIONS);

        if (statusChange.getElectionStatus() == ElectionStatus.ENDED) {
            throw new InvalidUpdate("This election has already ended and cannot be modified.");
        }

        if (statusChange.getElectionStatus() == ElectionStatus.RESULTS_RELEASED) {
            throw new InvalidUpdate("Results have already been released for this election.");
        }

        // Enforce valid status transition path:
        // DRAFT -> UPCOMING -> LIVE -> ENDED -> RESULTS_RELEASED
        java.util.Map<ElectionStatus, java.util.Set<ElectionStatus>> allowedTransitions = java.util.Map.of(
                ElectionStatus.DRAFT,            java.util.Set.of(ElectionStatus.UPCOMING, ElectionStatus.LIVE),
                ElectionStatus.UPCOMING,         java.util.Set.of(ElectionStatus.LIVE, ElectionStatus.DRAFT),
                ElectionStatus.LIVE,             java.util.Set.of(ElectionStatus.ENDED),
                ElectionStatus.ENDED,            java.util.Set.of(ElectionStatus.RESULTS_RELEASED),
                ElectionStatus.RESULTS_RELEASED, java.util.Set.of()
        );

        java.util.Set<ElectionStatus> validNext = allowedTransitions.getOrDefault(
                statusChange.getElectionStatus(), java.util.Set.of());

        if (!validNext.contains(newStatus)) {
            throw new InvalidUpdate(
                    "Invalid status transition: cannot move from "
                    + statusChange.getElectionStatus() + " to " + newStatus
                    + ". Allowed transitions: " + validNext);
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
                updaterOrg.getOrganizationId(),
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

    /**
     * System-initiated election close — no authenticated user required.
     * Called by {@link com.brandedhustlers.electionbackend.election.scheduler.ElectionAutoCloseScheduler}
     * when an election's end time has passed or its vote-count threshold has been reached.
     *
     * <p>Bypasses the auth/org ownership checks that {@link #changeElectionStatus} enforces,
     * but still validates the LIVE → ENDED transition and emits a full audit event
     * with actor type "SYSTEM".</p>
     */
    @Override
    @Transactional
    public void endElectionSystemAuto(UUID electionId, String reason) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() ->
                        new ElectionNotFound("Auto-close: election not found — " + electionId));

        // Guard: only LIVE elections can be moved to ENDED
        if (election.getElectionStatus() != ElectionStatus.LIVE) {
            log.warn("Auto-close skipped for election {} — status is {} (expected LIVE)",
                    electionId, election.getElectionStatus());
            return;
        }

        election.setElectionStatus(ElectionStatus.ENDED);
        Election saved = electionRepository.save(election);

        log.info("Election auto-closed [id={}, title='{}', reason={}]",
                saved.getElectionId(), saved.getElectionTitle(), reason);

        auditEmitter.emit(
                null,
                "election.auto_closed",
                null,
                saved.getOrganization().getOrganizationId(),
                "Election automatically closed by system scheduler — reason: " + reason,
                null,              // no human actor
                "SYSTEM_SCHEDULER",
                "SYSTEM",
                "SYSTEM",
                "Election",
                saved.getElectionId(),
                saved.getElectionTitle(),
                "SCHEDULER",
                null,
                null,
                null,
                "INFO",
                1);
    }

    @Override
    @Transactional
    public ElectionDTO addCandidate(UUID electionId, CandidateDTO newCandidate, MultipartFile candidateImage)
            throws IOException {
        AppUser creator = getUser.getCurrentAuthenticatedUser();

        Election election = electionRepository.findById(electionId)
                .orElseThrow(
                        () -> new ElectionNotFound("An election with this ID: " + electionId + " does not exist."));

        util.requireAdminOrPermission(creator, PermissionType.EDIT_ELECTIONS);

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

        Organization creatorOrg = creator.getActiveOrganization();
        auditEmitter.emit(
                null,
                "election.election_updated",
                null,
                creatorOrg != null ? creatorOrg.getOrganizationId() : null,
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

//    @org.springframework.cache.annotation.Cacheable(value = "elections", key = "#organizationId")
    public Page<ElectionDTO> getElectionsByOrg(UUID organizationId, Pageable pageable) {
        List<ElectionDTO> elections = electionRepository.findByOrganizationId(organizationId)
                .stream()
                .map(electionMapper::entityToDto)
                .toList();
        return new PageImpl<>(elections, pageable, elections.size());
    }

//    @org.springframework.cache.annotation.Cacheable(value = "liveElections")
    public Page<ElectionDTO> getLiveElectionForVoterOrg(Pageable pageable) {

        Voter currentVoter = getUser.getCurrentAuthenticatedVoter();
        UUID voterOrgId = currentVoter.getOrganization().getOrganizationId();
        UUID voterId = currentVoter.getVoterId();

        List<ElectionDTO> eligibleElections = electionRepository.findLiveByOrgAndNotVoted(
                voterOrgId,
                ElectionStatus.LIVE,
                voterId)
                .stream()
                .filter(election -> {
                    if (election.getEligibilityPolicy() == null) {
                        return true;
                    }
                    return eligibilityService
                            .evaluate(currentVoter.getAttributes(), election.getEligibilityPolicy())
                            .isAllowed();
                })
                .map(electionMapper::entityToDto)
                .toList();

        return new PageImpl<>(eligibleElections, pageable, eligibleElections.size());
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

        // todo emit election live event so participants receive email ~~ I don't have to do this here...because in the launch transition that calls this method
        // todo the messaging listener picks the launch up, fetches the election and then send to the voters
        // todo think about which path is better though...
        log.info("Election {} transitioned to LIVE, config anchored", electionId);

        // return electionMapper.entityToDto(election);
    }

    /**
     * Dry-run eligibility check — lets admins verify a voter's eligibility before go-live.
     * Does not cast any vote or mutate any state.
     *
     * @return {allowed, reason} — reason is null when allowed=true
     */
    public EligibilityService.EligibilityResult checkEligibility(UUID electionId, UUID voterId) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(actor, PermissionType.EDIT_ELECTIONS);

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionNotFound("Election not found: " + electionId));

        com.brandedhustlers.electionbackend.voter.domain.Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new IllegalArgumentException("Voter not found: " + voterId));

        if (election.getEligibilityPolicy() == null) {
            return EligibilityService.EligibilityResult.allow();
        }

        return eligibilityService.evaluate(voter.getAttributes(), election.getEligibilityPolicy());
    }
}
