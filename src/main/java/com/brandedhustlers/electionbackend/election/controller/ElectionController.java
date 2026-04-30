package com.brandedhustlers.electionbackend.election.controller;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.RequiresFeature;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.UpdateCandidateCmd;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.CandidateService;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
import com.brandedhustlers.electionbackend.voter.service.VoterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/election")
public class ElectionController {
    private final ElectionService electionService;
    private final ObjectMapper objectMapper;
    private final AppUserRepository appUserRepository;
    private final GetUser getUser;
    private final CandidateService candidateService;
    private final ElectionRepository electionRepository;
    private final VoterService voterService;

    public ElectionController(ElectionService electionService,
            ObjectMapper objectMapper,
            AppUserRepository appUserRepository, GetUser getUser, CandidateService candidateService,
            ElectionRepository electionRepository, VoterService voterService) {
        this.electionService = electionService;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
        this.getUser = getUser;
        this.candidateService = candidateService;
        this.electionRepository = electionRepository;
        this.voterService = voterService;
    }

    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @PostMapping("/create")
    public ResponseEntity<?> createElection(
            @RequestBody @Valid CreateElectionRequest request) {
        return new ResponseEntity<>(electionService.createElection(request), HttpStatus.CREATED);
    }

    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @PutMapping("/update/{electionId}")
    public ResponseEntity<?> updateElection(
            @Valid @RequestBody UpdateElection election,
            @PathVariable UUID electionId) {
        return new ResponseEntity<>(electionService.updateElection(electionId, election), HttpStatus.OK);
    }

    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @PutMapping("/update-status/{electionId}")
    public ResponseEntity<?> changeStatus(
            @PathVariable UUID electionId,
            @RequestParam ElectionStatus electionStatus) {
        return new ResponseEntity<>(electionService.changeElectionStatus(electionId, electionStatus), HttpStatus.OK);
    }

    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @PostMapping("/candidate-add/{electionId}")
    public ResponseEntity<?> addCandidate(
            @PathVariable UUID electionId,
            @RequestPart(name = "candidate") String candidate,
            @RequestPart(name = "candidateImage", required = false) MultipartFile candidateImage) throws IOException {
        CandidateDTO candidateDTO = objectMapper.readValue(candidate, CandidateDTO.class);
        return new ResponseEntity<>(
                electionService.addCandidate(electionId,
                        candidateDTO,
                        candidateImage),
                HttpStatus.CREATED);
    }

    @GetMapping("/{electionId}")
    public ResponseEntity<?> getElection(@PathVariable UUID electionId) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (orgId == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "forbidden",
                    "message", "No active organization. Please join or switch to an organization first."));
        }
        return ResponseEntity.ok(electionService.getElectionById(orgId, electionId));
    }

    @GetMapping("/get-all")
    public ResponseEntity<?> getOrgElection(Pageable pageable) {
        // Use the AppUser from the security context — it was loaded by JWTAuthFilter →
        // loadUserByUsername (@Transactional) which force-initialized userOrganizations.
        // Calling appUserRepository.findById() here returns a NEW detached AppUser with
        // cold lazy collections, causing LazyInitializationException on getActiveOrganization().
        AppUser launchCreator = getUser.getCurrentAuthenticatedUser();

        Organization creatorOrg = launchCreator.getActiveOrganization();
        if (creatorOrg == null) {
            return ResponseEntity.status(403).body(Map.of("status", "forbidden",
                    "message", "No active organization. Please join or switch to an organization first."));
        }
        return new ResponseEntity<>(electionService.getElectionsByOrg(creatorOrg.getOrganizationId(), pageable),
                HttpStatus.OK);
    }

    @GetMapping("/live")
    public ResponseEntity<?> live(Pageable pageable) {
        return new ResponseEntity<>(electionService.getLiveElectionForVoterOrg(pageable), HttpStatus.OK);
    }

    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @PutMapping("/update-candidate/{candidateId}")
    public ResponseEntity<?> updateCandidateDuringLiveElection(@RequestBody UpdateCandidateCmd updateCandidateCmd,
            @PathVariable UUID candidateId) {
        AppUser admin = appUserRepository.findById(getUser.getCurrentAuthenticatedUserId())
                .orElseThrow(() -> new UserNotFound("Invalid org admin not found."));
        return new ResponseEntity<>(candidateService.updateCandidate(
                candidateId,
                updateCandidateCmd,
                admin), HttpStatus.OK);
    }

    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @PostMapping("/live/{electionId}")
    public ResponseEntity<?> bringElectionLive(@PathVariable UUID electionId) {
        try {
            // Get authenticated user
            AppUser user = getUser.getCurrentAuthenticatedUser();

            // Verify admin role — SUPER_ADMIN, ADMIN, and ORG_ADMIN can all bring elections live
            if (!user.getUserRole().equals(UserRole.ADMIN)
                    && !user.getUserRole().equals(UserRole.SUPER_ADMIN)
                    && !user.getUserRole().equals(UserRole.ORG_ADMIN)) {
                return ResponseEntity.status(403).body(Map.of(
                        "status", "forbidden",
                        "message", "Only admins can bring elections live"));
            }

            // Verify org access (admin must belong to election's org)
            Organization electionOrg = electionRepository.getElectionOrganization(electionId);
            Organization userOrg = user.getActiveOrganization();

            if (userOrg == null) {
                return ResponseEntity.status(403).body(Map.of(
                        "status", "forbidden",
                        "message", "No active organization. Please join or switch to an organization first."));
            }

            log.info("org match: {}", electionOrg.getOrganizationId().equals(userOrg.getOrganizationId()));
            log.info("election org id: {}", electionOrg.getOrganizationId());
            log.info("user org id: {}", userOrg.getOrganizationId());

            if (!(userOrg.getOrganizationId().equals(electionOrg.getOrganizationId()))) {
                return ResponseEntity.status(403).body(Map.of(
                        "status", "forbidden",
                        "message", "You cannot manage elections from other organizations"));
            }

            // Transition to live (anchors config to blockchain)
            electionService.transitionToLive(electionId, user.getAppUserId());

            log.info("Election {} brought live by admin {}", electionId, user.getAppUserId());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Election is now live"));

        } catch (IllegalStateException e) {
            // Election already live or invalid status transition
            log.warn("Cannot bring election {} live: {}", electionId, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to bring election {} live", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Internal server error: " + e.getMessage()));
        }
    }

    /** Dry-run eligibility check — no state mutation. Requires ADMIN+ role. */
    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @GetMapping("/{electionId}/eligibility/check")
    public ResponseEntity<?> checkEligibility(
            @PathVariable UUID electionId,
            @RequestParam UUID voterId) {
        var result = electionService.checkEligibility(electionId, voterId);
        return ResponseEntity.ok(Map.of(
                "allowed", result.isAllowed(),
                "reason", result.getReason() == null ? "" : result.getReason()));
    }

    /** Voter turnout stats for an election. Requires ADMIN+ role. */
    @RequiresFeature(Feature.ELECTION_MANAGEMENT)
    @GetMapping("/{electionId}/turnout")
    public ResponseEntity<?> getTurnout(@PathVariable UUID electionId) {
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        if (orgId == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "forbidden",
                    "message", "No active organization. Please join or switch to an organization first."));
        }
        return ResponseEntity.ok(voterService.getTurnout(orgId, electionId));
    }
}
