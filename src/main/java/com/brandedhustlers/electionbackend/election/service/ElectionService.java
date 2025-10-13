package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;

import java.util.UUID;

public class ElectionService implements IElectionService{
    @Override
    public ElectionDTO creatElection(CreateElectionRequest request) {
        return null;
    }

    @Override
    public ElectionDTO updateElection(UUID electionID, UpdateElection request) {
        return null;
    }

    @Override
    public ElectionDTO changeElectionStatus(UUID electionId, ElectionStatus newStatus) {
        return null;
    }

    @Override
    public ElectionDTO addCandidate(UUID electionId, CandidateDTO newCandidate) {
        return null;
    }
}
