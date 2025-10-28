package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoterId;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.mapper.VoterMapper;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VoterService implements IVoterService {

    private final AppUserRepository appUserRepository;
    private final VoterMapper voterMapper;
    private final VoterRepository voterRepository;
    private final AuditEmitter auditEmitter;
    private final ElectionVoterRepository electionVoterRepository;
    private final ElectionRepository electionRepository;

    public VoterService(AppUserRepository appUserRepository,
                        VoterMapper voterMapper,
                        VoterRepository voterRepository,
                        AuditEmitter auditEmitter,
                        ElectionVoterRepository electionVoterRepository,
                        ElectionRepository electionRepository) {
        this.appUserRepository = appUserRepository;
        this.voterMapper = voterMapper;
        this.voterRepository = voterRepository;
        this.auditEmitter = auditEmitter;
        this.electionVoterRepository = electionVoterRepository;
        this.electionRepository = electionRepository;
    }


    @Override
    @Transactional
    public VoterDTO create(UUID orgId, CreateVoterCmd cmd, AppUser actor) {
        if (!(authorizeUser(actor, UserRole.ADMIN) || authorizeUser(actor, UserRole.SUPER_ADMIN))) {
            throw new AccessDeniedException("You do not have authority to create a voter");
        }

        if (actor.getAppUserOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create a voter");
        }

        Voter newVoter = Voter.builder()
                .name(cmd.name())
                .phone(cmd.phone())
                .email(cmd.email())
                .organization(actor.getAppUserOrganization())
                .nationalId(cmd.nationalId())
                .voterStatus(VoterStatus.INACTIVE)
                .createdBy(actor)
                .build();

        voterRepository.save(newVoter);

        //Todo debug phantom logs

        UUID actorId = actor.getAppUserId();
        String actorName = actor.getFirstName();
        String actorRole = actor.getUserRole().name();
        String actorType = "USER";

        String source = "WEB";
        String ip = null;
        String ua = null;

        auditEmitter.emit(
                null,
                "voters.voter_created",
                null,
                orgId,
                "New Voter Created in org.",
                actorId,
                actorName,
                actorRole,
                actorType,
                "Voter",
                null,
                "Created a new voter in org",
                source,
                null,
                ip,
                ua,
                "INFO",
                1
        );
        return voterMapper.entityToDto(newVoter);
    }

    @Override
    public List<VoterDTO> search(UUID orgId, String q, int limit) {
        return List.of();
    }

    @Override
    @Transactional
    public void assignToElection(UUID orgId, AssignVotersCmd cmd, AppUser actor) {
        // Authorization
        if (!authorizeUser(actor, UserRole.ADMIN) && !authorizeUser(actor, UserRole.SUPER_ADMIN)) {
            throw new AccessDeniedException("You do not have authority to assign voters");
        }

        UUID actorOrgId = Optional.ofNullable(actor.getAppUserOrganization())
                .orElseThrow(() -> new AccessDeniedException("You must belong to an organization"))
                .getOrganizationId();

        if (!actorOrgId.equals(orgId)) {
            throw new AccessDeniedException("Organization mismatch");
        }

        // Validation
        if (cmd.voterIds() == null || cmd.voterIds().isEmpty()) {
            throw new IllegalArgumentException("Voter IDs cannot be empty");
        }

        if (!electionRepository.existsByElectionIdAndOrganization_OrganizationId(cmd.electionId(), orgId)) {
            throw new EntityNotFoundException("Election not found");
        }

        // Find existing voters
        List<UUID> existingVoters = voterRepository.findAllByVoterIdIn(cmd.voterIds(), orgId);

        if (existingVoters.isEmpty()) {
            throw new IllegalArgumentException("None of the specified voters exist in this organization");
        }

        // Check for already assigned voters
        List<UUID> alreadyAssigned = electionVoterRepository
                .findAssignedVoterIds(cmd.electionId(), existingVoters);

        List<UUID> votersToAssign = existingVoters.stream()
                .filter(voterId -> !alreadyAssigned.contains(voterId))
                .toList();

        if (votersToAssign.isEmpty()) {
            throw new IllegalArgumentException("All specified voters are already assigned to this election");
        }

        // Create election voter assignments
        List<ElectionVoter> voters = votersToAssign.stream()
                .map(voterId -> {
                    ElectionVoterId embeddedId = new ElectionVoterId();
                    embeddedId.setElectionId(cmd.electionId());
                    embeddedId.setVoterId(voterId);

                    ElectionVoter voter = new ElectionVoter();
                    voter.setId(embeddedId);
                    voter.setStationId(null);
                    voter.setVerifiedAt(null);
                    voter.setHasVoted(false);
                    return voter;
                })
                .toList();

        electionVoterRepository.saveAll(voters);

        auditEmitter.emit(
                null,
                "voters.voters_assigned_to_election",
                null,
                orgId,
                String.format("Assigned %d voters to election", voters.size()),
                actor.getAppUserId(),
                actor.getFirstName(),
                actor.getUserRole().name(),
                "USER",
                "ElectionVoter",
                null,
                "Assigned voters to an election",
                "WEB",
                null,
                null,
                null,
                "INFO",
                1
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
