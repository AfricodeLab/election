package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.mapper.VoterMapper;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
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


    public VoterService(
            VoterMapper voterMapper,
            VoterRepository voterRepository,
            AuditEmitter auditEmitter,
            AttributeExtractor attributeExtractor,
            GetUser getUser, Util util) {
        this.util = util;
        this.voterMapper = voterMapper;
        this.voterRepository = voterRepository;
        this.auditEmitter = auditEmitter;
        this.attributeExtractor = attributeExtractor;
        this.getUser = getUser;
    }


    @Override
    @Transactional
    public VoterDTO create(UUID orgId, CreateVoterCmd cmd) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        if (!(util.authorizeUser(actor, UserRole.ADMIN)
                || util.authorizeUser(actor, UserRole.SUPER_ADMIN)
                || util.authorizeUser(actor, UserRole.ORG_ADMIN))) {
            throw new AccessDeniedException("You do not have authority to create a voter");
        }

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
    public List<VoterDTO> search(UUID orgId, String q, int limit) {
        return List.of();
    }

   /* @Override
    @Transactional
    public void assignToElection(UUID orgId, AssignVotersCmd cmd) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        // Authorization
        if (!authorizeUser(actor, UserRole.ADMIN) && !authorizeUser(actor, UserRole.SUPER_ADMIN)) {
            throw new AccessDeniedException("You do not have authority to assign voters");
        }

        UUID actorOrgId = Optional.ofNullable(actor.getActiveOrganization())
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
*/
}
