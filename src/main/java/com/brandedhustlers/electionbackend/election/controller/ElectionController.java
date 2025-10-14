package com.brandedhustlers.electionbackend.election.controller;

import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
    private final JWTService jwtService;
    private final HttpServletRequest httpServletRequest;
    private final ObjectMapper objectMapper;

    public ElectionController(ElectionService electionService,
                              JWTService jwtService,
                              HttpServletRequest httpServletRequest,
                              ObjectMapper objectMapper) {
        this.electionService = electionService;
        this.jwtService = jwtService;
        this.httpServletRequest = httpServletRequest;
        this.objectMapper = objectMapper;
    }
    private UUID getAppUserId(HttpServletRequest request) {
        return jwtService.extractAppUserId(request);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createElection(
            @RequestBody CreateElectionRequest request
            ){
        return new ResponseEntity<>(electionService.createElection(request, getAppUserId(httpServletRequest)), HttpStatus.CREATED);
    }

    @PutMapping("/update/{electionId}")
    public ResponseEntity<?> updateElection(
            @RequestBody UpdateElection election,
            @PathVariable UUID electionId
            ){

        return new ResponseEntity<>(electionService.updateElection(electionId, election, getAppUserId(httpServletRequest)), HttpStatus.OK);
    }

    @PutMapping("/update-status/{electionId}")
    public ResponseEntity<?> changeStatus(
            @PathVariable UUID electionId,
            @RequestParam ElectionStatus electionStatus
            ){
        return new ResponseEntity<>(electionService.changeElectionStatus(electionId, electionStatus, getAppUserId(httpServletRequest)), HttpStatus.OK);
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
                        candidateImage,
                        getAppUserId(httpServletRequest)),
                HttpStatus.CREATED);
    }
}
