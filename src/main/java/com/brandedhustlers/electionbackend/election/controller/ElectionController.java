package com.brandedhustlers.electionbackend.election.controller;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/election")
public class ElectionController {
    private final ElectionService electionService;
    private final ObjectMapper objectMapper;
    private final AppUserRepository appUserRepository;
    private final GetUser getUser;

    public ElectionController(ElectionService electionService,
                              ObjectMapper objectMapper,
                              AppUserRepository appUserRepository, GetUser getUser) {
        this.electionService = electionService;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
        this.getUser = getUser;
    }


    @PostMapping("/create")
    public ResponseEntity<?> createElection(
            @RequestBody CreateElectionRequest request
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

        Organization creatorOrg = launchCreator.getAppUserOrganization();
        return new ResponseEntity<>(electionService.getElectionsByOrg(creatorOrg.getOrganizationId(), pageable), HttpStatus.OK);
    }

    @GetMapping("/live")
    public ResponseEntity<?> live(HttpServletRequest httpServletRequest, Pageable pageable) {
        return new ResponseEntity<>(electionService.getLiveElectionForVoterOrg(httpServletRequest, pageable), HttpStatus.OK);
    }
}
