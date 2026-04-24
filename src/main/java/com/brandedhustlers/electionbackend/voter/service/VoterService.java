package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoterId;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.TurnoutDTO;
import com.brandedhustlers.electionbackend.voter.dto.UpdateVoterContactCmd;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.mapper.VoterMapper;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class VoterService implements IVoterService {

    private final VoterMapper voterMapper;
    private final VoterRepository voterRepository;
    private final AuditEmitter auditEmitter;
    private final AttributeExtractor attributeExtractor;
    private final GetUser getUser;
    private final Util util;
    private final ElectionRepository electionRepository;
    private final ElectionVoterRepository electionVoterRepository;


    public VoterService(
            VoterMapper voterMapper,
            VoterRepository voterRepository,
            AuditEmitter auditEmitter,
            AttributeExtractor attributeExtractor,
            GetUser getUser,
            Util util,
            ElectionRepository electionRepository,
            ElectionVoterRepository electionVoterRepository) {
        this.util = util;
        this.voterMapper = voterMapper;
        this.voterRepository = voterRepository;
        this.auditEmitter = auditEmitter;
        this.attributeExtractor = attributeExtractor;
        this.getUser = getUser;
        this.electionRepository = electionRepository;
        this.electionVoterRepository = electionVoterRepository;
    }


    @Override
    @Transactional
    public VoterDTO create(UUID orgId, CreateVoterCmd cmd) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        util.requireAdminOrPermission(actor, PermissionType.CREATE_VOTER);

        if (actor.getActiveOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create a voter");
        }

        /**
         * NEW: Extract/compute attributes from voter data.
         * This combines:
         * - Auto-derived attributes (age from ID, region from phone)
         * - Manually provided attributes (from cmd.attributes())
         */
        Map<String, Object> attributes = attributeExtractor.extractAttributes(
                cmd.nationalId(),
                cmd.phone(),
                cmd.email(),
                cmd.attributes()
        );

        log.info("Creating voter with attributes: {}", attributes);

        Voter newVoter = Voter.builder()
                .name(cmd.name())
                .phone(cmd.phone())
                .email(cmd.email())
                .organization(actor.getActiveOrganization())
                .nationalId(cmd.nationalId())
                .voterStatus(VoterStatus.ACTIVE)
                .createdBy(actor)
                .attributes(attributes)
                .attributesSource("MANUAL_CREATION")
                .attributesUpdatedAt(Timestamp.from(Instant.now()))
                .build();

        voterRepository.save(newVoter);

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

    public Page<VoterDTO> getOrgVoters(Pageable pageable) {
        UUID voterOrg = getUser.getCurrentAuthenticatedUserOrgId();

        List<VoterDTO> orgVoters = voterRepository.findByOrgId(voterOrg)
                .stream()
                .map(voterMapper::entityToDto)
                .toList();

        return new PageImpl<>(orgVoters, pageable, orgVoters.size());
    }

    @Override
    public VoterDTO getById(UUID orgId, UUID voterId) {
        // findVoterByIdAndOrgId enforces org isolation via JPQL — no separate check needed
        Voter voter = voterRepository.findVoterByIdAndOrgId(voterId, orgId)
                .orElseThrow(() -> new EntityNotFoundException("Voter not found: " + voterId));
        return voterMapper.entityToDto(voter);
    }

    @Override
    public List<VoterDTO> search(UUID orgId, String q, int limit) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return voterRepository.findByOrgIDAndNameContainingIgnoreCase(orgId, q.trim())
                .stream()
                .limit(limit > 0 ? limit : 20)
                .map(voterMapper::entityToDto)
                .toList();
    }

    @Transactional
    public VoterDTO updateContact(UUID orgId, UUID voterId, UpdateVoterContactCmd cmd) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(actor, PermissionType.UPDATE_VOTER);

        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new EntityNotFoundException("Voter not found: " + voterId));

        util.requireOrgMatch(actor, voter.getOrganization().getOrganizationId());

        if (cmd.email() != null && !cmd.email().isBlank()) {
            voterRepository.findByEmailIgnoreCase(cmd.email()).ifPresent(conflict -> {
                if (!conflict.getVoterId().equals(voterId)) {
                    throw new IllegalArgumentException("Email address is already in use by another voter");
                }
            });
            voter.setEmail(cmd.email().trim().toLowerCase());
        }

        if (cmd.phone() != null && !cmd.phone().isBlank()) {
            voterRepository.findByPhone(cmd.phone().trim()).ifPresent(conflict -> {
                if (!conflict.getVoterId().equals(voterId)) {
                    throw new IllegalArgumentException("Phone number is already in use by another voter");
                }
            });
            voter.setPhone(cmd.phone().trim());
        }

        voterRepository.save(voter);
        log.info("Contact info updated for voter {} by admin {}", voterId, actor.getAppUserId());
        return voterMapper.entityToDto(voter);
    }

    public TurnoutDTO getTurnout(UUID orgId, UUID electionId) {
        electionRepository.findByElectionId(electionId)
                .filter(e -> e.getOrganization().getOrganizationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Election not found in this organization"));

        long registered = electionVoterRepository.countByElectionId(electionId);
        long voted = electionVoterRepository.countVotedByElectionId(electionId);
        double pct = registered == 0 ? 0.0 : Math.round((voted * 100.0 / registered) * 10.0) / 10.0;

        return new TurnoutDTO(electionId, registered, voted, pct);
    }

    @Override
    @Transactional
    public void assignToElection(UUID orgId, AssignVotersCmd cmd) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        util.requireAdminOrPermission(actor, PermissionType.ASSIGN_VOTERS);

        // ORG_ADMIN: their active org must match the target orgId
        // ADMIN/SUPER_ADMIN: bypass, they target any org directly
        if (!util.isPlatformAdmin(actor)) {
            UUID actorOrgId = java.util.Optional.ofNullable(actor.getActiveOrganization())
                    .orElseThrow(() -> new AccessDeniedException("You must belong to an organization"))
                    .getOrganizationId();
            if (!actorOrgId.equals(orgId)) {
                throw new AccessDeniedException("Organization mismatch");
            }
        }

        Election election = electionRepository.findByElectionId(cmd.electionId())
                .orElseThrow(() -> new EntityNotFoundException("Election not found"));

        // Data integrity: election must belong to the target org regardless of caller role
        if (!election.getOrganization().getOrganizationId().equals(orgId)) {
            throw new AccessDeniedException("Election does not belong to your organization");
        }

        // Resolve Voter entities that exist in this org from the supplied IDs
        List<Voter> existingVoters = voterRepository.findAllByVoterIdIn(cmd.voterIds(), orgId);

        if (existingVoters.isEmpty()) {
            throw new IllegalArgumentException("None of the specified voters exist in this organization");
        }

        List<UUID> existingVoterIds = existingVoters.stream().map(Voter::getVoterId).toList();

        // Skip voters who are already assigned
        List<UUID> alreadyAssigned = electionVoterRepository
                .findAssignedVoterIds(cmd.electionId(), existingVoterIds);

        List<Voter> votersToAssign = existingVoters.stream()
                .filter(v -> !alreadyAssigned.contains(v.getVoterId()))
                .toList();

        if (votersToAssign.isEmpty()) {
            throw new IllegalArgumentException("All specified voters are already assigned to this election");
        }

        List<ElectionVoter> assignments = votersToAssign.stream()
                .map(voter -> ElectionVoter.builder()
                        .id(new ElectionVoterId(cmd.electionId(), voter.getVoterId()))
                        .voter(voter)
                        .election(election)
                        .stationId(null)
                        .verifiedAt(null)
                        .votedAt(null)
                        .hasVoted(false)
                        .build())
                .toList();

        electionVoterRepository.saveAll(assignments);
        log.info("Assigned {} voters to election {}", assignments.size(), cmd.electionId());

        auditEmitter.emit(
                null,
                "voters.voters_assigned_to_election",
                null,
                orgId,
                String.format("Assigned %d voters to election", assignments.size()),
                actor.getAppUserId(),
                actor.getFirstName(),
                actor.getUserRole().name(),
                "USER",
                "ElectionVoter",
                cmd.electionId(),
                "Assigned voters to an election",
                "WEB",
                null,
                null,
                null,
                "INFO",
                1
        );
    }
}
