package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface IElectionService {
    ElectionDTO createElection(CreateElectionRequest request);

    ElectionDTO updateElection(UUID electionID, UpdateElection request);

    ElectionDTO changeElectionStatus(UUID electionId, ElectionStatus newStatus);

    ElectionDTO addCandidate(UUID electionId, CandidateDTO newCandidate, MultipartFile candidateImage) throws IOException;


}
