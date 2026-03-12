package com.brandedhustlers.electionbackend.election.controller;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.UpdateCandidateCmd;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.CandidateService;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
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

    public ElectionController(ElectionService electionService,
                              ObjectMapper objectMapper,
                              AppUserRepository appUserRepository, GetUser getUser, CandidateService candidateService, ElectionRepository electionRepository) {
        this.electionService = electionService;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
        this.getUser = getUser;
        this.candidateService = candidateService;
        this.electionRepository = electionRepository;
    }


    @PostMapping("/create")
    public ResponseEntity<?> createElection(
            @RequestBody @Valid CreateElectionRequest request
    ) {
        return new ResponseEntity<>(electionService.createElection(request), HttpStatus.CREATED);
    }

    @PutMapping("/update/{electionId}")
    public ResponseEntity<?> updateElection(
            @RequestBody UpdateElection election,
            @PathVariable UUID electionId
    ) {
        return new ResponseEntity<>(electionService.updateElection(electionId, election), HttpStatus.OK);
    }

    @PutMapping("/update-status/{electionId}")
    public ResponseEntity<?> changeStatus(
            @PathVariable UUID electionId,
            @RequestParam ElectionStatus electionStatus
    ) {
        return new ResponseEntity<>(electionService.changeElectionStatus(electionId, electionStatus), HttpStatus.OK);
    }

    @PostMapping("/candidate-add/{electionId}")
    public ResponseEntity<?> addCandidate(
            @PathVariable UUID electionId,
            @RequestPart(name = "candidate") String candidate,
            @RequestPart(name = "candidateImage", required = false) MultipartFile candidateImage
    ) throws IOException {
        CandidateDTO candidateDTO = objectMapper.readValue(candidate, CandidateDTO.class);
        return new ResponseEntity<>(
                electionService.addCandidate(electionId,
                        candidateDTO,
                        candidateImage),
                HttpStatus.CREATED);
    }

    @GetMapping("/get-all")
    public ResponseEntity<?> getOrgElection(Pageable pageable) {
        AppUser launchCreator = appUserRepository.findById(getUser.getCurrentAuthenticatedUserId())
                .orElseThrow(() -> new UserNotFound("User not found."));

        Organization creatorOrg = launchCreator.getActiveOrganization();
        return new ResponseEntity<>(electionService.getElectionsByOrg(creatorOrg.getOrganizationId(), pageable), HttpStatus.OK);
    }

    @GetMapping("/live")
    public ResponseEntity<?> live(Pageable pageable) {
        return new ResponseEntity<>(electionService.getLiveElectionForVoterOrg(pageable), HttpStatus.OK);
    }

    @PutMapping("/update-candidate/{candidateId}")
    public ResponseEntity<?> updateCandidateDuringLiveElection(@RequestBody UpdateCandidateCmd updateCandidateCmd,
                                                               @PathVariable UUID candidateId){
        AppUser admin = appUserRepository.findById(getUser.getCurrentAuthenticatedUserId())
                .orElseThrow(() -> new UserNotFound("Invalid org admin not found."));
        return new ResponseEntity<>(candidateService.updateCandidate(
                candidateId,
                updateCandidateCmd,
                admin
        ), HttpStatus.OK);
    }

    @PostMapping("/live/{electionId}")
    public ResponseEntity<?> bringElectionLive(@PathVariable UUID electionId) {
        try {
            // Get authenticated user
            AppUser user = getUser.getCurrentAuthenticatedUser();

            // Verify admin role
            if (!user.getUserRole().equals(UserRole.ADMIN) && !user.getUserRole().equals(UserRole.SUPER_ADMIN)) {
                return ResponseEntity.status(403).body(Map.of(
                        "status", "forbidden",
                        "message", "Only admins can bring elections live"
                ));
            }

            // Verify org access (admin must belong to election's org)
            Organization electionOrg = electionRepository.getElectionOrganization(electionId);

            log.info(String.valueOf(electionOrg.equals(user.getActiveOrganization())));
            log.info("election org: {}", electionOrg.toString());
            log.info("user org: {}", user.getActiveOrganization().toString());

            if (!(user.getActiveOrganization().getOrganizationId().equals(electionOrg.getOrganizationId()))) {
                return ResponseEntity.status(403).body(Map.of(
                        "status", "forbidden",
                        "message","You cannot manage elections from other organizations"
                ));
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
                    "message", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Failed to bring election {} live", electionId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
}
