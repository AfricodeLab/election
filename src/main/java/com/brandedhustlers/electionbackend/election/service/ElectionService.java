package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ElectionService implements IElectionService {
    private final ElectionRepository electionRepository;
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;
    private final HttpServletRequest httpServletRequest;
    private final ElectionMapper electionMapper;
    private final CloudinaryService cloudinaryService;
    private final CandidateRepository candidateRepository;

    public ElectionService(ElectionRepository electionRepository,
                           JWTService jwtService,
                           AppUserRepository appUserRepository,
                           HttpServletRequest httpServletRequest,
                           ElectionMapper electionMapper,
                           CloudinaryService cloudinaryService, CandidateRepository candidateRepository) {
        this.electionRepository = electionRepository;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.httpServletRequest = httpServletRequest;
        this.electionMapper = electionMapper;
        this.cloudinaryService = cloudinaryService;
        this.candidateRepository = candidateRepository;
    }

    @Override
    @Transactional
    public ElectionDTO createElection(CreateElectionRequest request, UUID userId) {
        AppUser creator = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User account not found"));

        if (!(authorizeUser(creator, UserRole.ADMIN) || authorizeUser(creator, UserRole.SUPER_ADMIN))) {
            throw new AccessDeniedException("You do not have authority to create an election");
        }

        if (creator.getAppUserOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create an election");
        }

        Organization electionOrg = creator.getAppUserOrganization();
        Election newElection = Election.builder()
                .electionTitle(request.electionTitle())
                .electionDescription(request.electionDescription())
                .electionType(request.electionType())
                .electionStatus(ElectionStatus.DRAFT)
                .electionStartTime(request.startTime())
                .electionEndTime(request.endtime())
                .expectedVoterCount(request.expectedVoterCount())
                .isPubliclyVisible(request.isPubliclyVisible())
                .shouldEndAutomatically(request.shouldEndAutomatically())
                .organization(electionOrg)
                .createdBy(creator)
                .build();

        if (!request.startTime().toLocalDateTime().isBefore(request.endtime().toLocalDateTime())) {
            throw new InvalidUpdate("Start time must be before end time");
        }

        if (electionRepository.existsByElectionTitleAndOrganization(
                request.electionTitle(), creator.getAppUserOrganization())) {
            throw new InvalidUpdate("An election with this title already exists in your organization");
        }

        return electionMapper.entityToDto(
                electionRepository.save(newElection)
        );
    }

    @Override
    public ElectionDTO updateElection(UUID electionID, UpdateElection request, UUID appUserId) {
        AppUser updater = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User account not found"));

        Election electionToUpdate = electionRepository.findById(electionID)
                .orElseThrow(() -> new ElectionNotFound("An election with this ID: " + electionID + " does not exist"));

        if (!electionToUpdate.getOrganization().getOrganizationId().equals(updater.getAppUserOrganization().getOrganizationId())) {
            throw new AccessDeniedException("You cannot manage elections outside your organization");
        }

        requireAdminOrPermission(updater, PermissionType.EDIT_ELECTIONS);

        if (!electionToUpdate.getElectionStatus().equals(ElectionStatus.DRAFT)) {
            throw new InvalidUpdate("The selected election is not in draft and can no longer be updated");
        }
        //updateStation can happen now all checks have been made
        electionToUpdate.setElectionTitle(request.newElectionName());
        electionToUpdate.setElectionStatus(request.newElectionStatus());
        electionToUpdate.setElectionStartTime(request.newStartTime());
        electionToUpdate.setElectionEndTime(request.newEndtime());
        electionToUpdate.setUpdatedBy(updater);
        return electionMapper.entityToDto(
                electionRepository.save(electionToUpdate)
        );
    }

    @Override
    public ElectionDTO changeElectionStatus(UUID electionId, ElectionStatus newStatus, UUID appUserId) {
        AppUser updater = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User account not found"));

        Election statusChange = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionNotFound("An election with this ID: " + electionId + " does not exist."));

        if (!statusChange.getOrganization().getOrganizationId().equals(updater.getAppUserOrganization().getOrganizationId())) {
            throw new AccessDeniedException("You cannot manage elections outside your organization");
        }

        requireAdminOrPermission(updater, PermissionType.EDIT_ELECTIONS);

        if (statusChange.getElectionStatus() == ElectionStatus.ENDED) {
            throw new InvalidUpdate("This election has already ended and cannot be modified.");
        }

        if (!statusChange.getElectionStatus().equals(ElectionStatus.DRAFT)) {

        }
        statusChange.setElectionStatus(newStatus);

        return electionMapper.entityToDto(
                electionRepository.save(statusChange)
        );
    }

    @Override
    @Transactional
    public ElectionDTO addCandidate(UUID electionId, CandidateDTO newCandidate, MultipartFile candidateImage, UUID appUserId) throws IOException {
        AppUser creator = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User account not found"));

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionNotFound("An election with this ID: " + electionId + " does not exist."));

        requireAdminOrPermission(creator, PermissionType.EDIT_ELECTIONS);

        if (!election.getElectionStatus().equals(ElectionStatus.DRAFT)) {
            throw new InvalidUpdate("Cannot add candidates to an election that is not in the drafting stage.");
        }


        Candidate candidate = Candidate.builder()
                .affiliation(newCandidate.affiliation())
                .manifesto(newCandidate.manifesto())
                .candidatePhotoURL(candidateImage != null ? cloudinaryService.uploadFile(candidateImage) : null)
                .dateOfBirth(newCandidate.dateOfBirth())
                .isIncumbent(newCandidate.isIncumbent())
                .createdBy(creator)
                .build();
        //add to election
        List<Candidate> electionCandidates = election.getCandidates();
        candidate.setElection(election);


        electionCandidates.add(candidate);
        election.setCandidates(electionCandidates);

        return electionMapper.entityToDto(
                electionRepository.save(election)
        );
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
